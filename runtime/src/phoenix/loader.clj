(ns phoenix.loader
  (:require [phoenix.location :as l]
            [phoenix.merge :refer [deep-merge]]
            [clojure.tools.reader.edn :as edn]
            [clojure.tools.logging :as log]))

(defn try-slurp [slurpable]
  (try
    (slurp slurpable)

    (catch Exception e
      (log/warnf "Can't read config-file: '%s', ignoring..." slurpable)
      ::invalid-include)))

(defn parse-config [s]
  (when (string? s)
    (edn/read-string {:readers *data-readers*}
                     s)))

(defn load-config-source [config-source location]
  (some-> (try-slurp config-source)
          parse-config
          (l/select-location location)))

(defn includes [config]
  (->> config
       ((juxt :general :environment :host :user))
       (mapcat :phoenix/includes)))

(defn load-config-sources [initial-source location]
  (loop [[config-source & more-sources :as config-sources] [initial-source]
         loaded-sources #{}
         configs []]
    (if (empty? config-sources)
      configs

      (if-not config-source
        (recur more-sources loaded-sources configs)

        (let [new-config (load-config-source config-source location)]
          (recur (concat more-sources (->> (includes new-config)
                                           (remove #(contains? loaded-sources %))
                                           (remove #{::invalid-include})))

                 (conj loaded-sources config-source)
                 (conj configs new-config)))))))

(defn load-config [initial-source location]
  (-> (load-config-sources initial-source location)
      deep-merge
      ((juxt :general :host :user :environment))
      deep-merge
      (dissoc :phoenix/includes)))
