(ns rocks.pho.btc.whac-a-mole.watcher
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            
            [rocks.pho.btc.whac-a-mole.utils :as utils]
            [rocks.pho.btc.whac-a-mole.config :refer [env]]
            [rocks.pho.btc.whac-a-mole.models :as models]
            [rocks.pho.btc.whac-a-mole.config :as config]))

;; id for taging data batch : 18 bit
;; timestamp(second:10 bit) and 8 bit for (* 60 60 24 365) : 31536000
(mount/defstate batch-id :start (* 100000000 (quot (System/currentTimeMillis)
                                                   1000)))


(defn watch-once []
  (let [id batch-id
        _ (mount/start-with {#'batch-id (inc batch-id)})]
)
  (log/info "watcher once!")
  (let [name (.getName (java.lang.management.ManagementFactory/getRuntimeMXBean))
        pid (first (clojure.string/split name #"@"))]
    (spit (:watcher-pid-file env) pid)))
