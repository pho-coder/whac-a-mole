(ns rocks.pho.btc.whac-a-mole.watcher
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            
            [rocks.pho.btc.whac-a-mole.utils :as utils]
            [rocks.pho.btc.whac-a-mole.config :refer [env]]
            [rocks.pho.btc.whac-a-mole.models :as models]
            [rocks.pho.btc.whac-a-mole.config :as config]
            [rocks.pho.btc.whac-a-mole.tactics.realtime-detail :as rd]))

(mount/defstate last-fixed-kline-timestamp :start -1)

;; id for taging data batch : 18 bit
;; timestamp(second:10 bit) and 8 bit for (* 60 60 24 365) : 31536000
(mount/defstate batch-id :start (* 100000000 (quot (System/currentTimeMillis)
                                                   1000)))

;; klines data in one klines data path;
;; file name YYYY-MM-dd_HH
;; file content: 
;; functions used below
(defn get-all-klines-files [klines-data-path]
  (let [file (clojure.java.io/as-file klines-data-path)
        klines-files (.list file)]
    klines-files))

(defn log-fixed-klines
  "fixed klines if first line write N(ew) at tail else write S(uccessive)"
  [klines-list & {:keys [new?]
                  :or {new? false}}]
  (let [format "YYYY-MM-dd_HH"
        path (:fixed-klines-data-path env)
        first-one (first klines-list)
        file-name (utils/get-readable-time (:timestamp first-one) format)
        file-path (str path "/" file-name)]
    (if new?
      (spit file-path (str (models/kline2log first-one "N") "\n") :append true)
      (spit file-path (str (models/kline2log first-one "S") "\n") :append true))
    (doall (doseq [one (rest klines-list)]
             (let [file-name (utils/get-readable-time (:timestamp one) format)
                   file-path (str path "/" file-name)]
               (spit file-path (str (models/kline2log one "S") "\n") :append true)))))
  (log/info "log" (.size klines-list) "klines with tag" (if new? "N" "S")))

(defn log-current-klines
  "current klines write C(urrent) at every line's tail"
  [kline id]
  (let [format "YYYY-MM-dd_HH"
        path (:current-klines-data-path env)
        file-name (utils/get-readable-time (:timestamp kline) format)
        file-path (str path "/" file-name)]
    (spit file-path (str id "," (models/kline2log kline "C") "\n") :append true)
    (log/info "log one current kline:" kline "batch id:" id)))

(defn get-last-kline-timestamp-local [path]
  (let [files (get-all-klines-files path)
        max-file (reduce (fn [m o]
                           (if (> (compare o m) 0)
                             o
                             m))
                         "" files)]
    (if (= max-file "")
      -1
      (:timestamp (last (models/log2klines (slurp (str path "/" max-file))))))))

(defn klines-dealer [id]
  (let [now-timestamp (System/currentTimeMillis)
        diff-minutes (quot (- now-timestamp last-fixed-kline-timestamp)
                           (* 60 1000))
        now-klines (models/api2klines (utils/get-kline "001" (if (> diff-minutes
                                                                    config/API-MAX-KLINES-LENGTH)
                                                               (+ config/API-MAX-KLINES-LENGTH
                                                                  10)
                                                               (+ diff-minutes
                                                                  10))))
        newest-kline (last now-klines)
        fixed-klines (drop-last now-klines)
        last-fixed-kline-timestamp-index (utils/find-kline-timestamp fixed-klines last-fixed-kline-timestamp)
        cutted-klines (if (= -1 last-fixed-kline-timestamp-index)
                        fixed-klines
                        (nthrest fixed-klines (inc last-fixed-kline-timestamp-index)))]
    (when-not (empty? cutted-klines)
      (log-fixed-klines cutted-klines :new? (if (= -1 last-fixed-kline-timestamp-index)
                                        (do (log/warn "klines dealer NOT FOUND last fixed kline timestamp:" last-fixed-kline-timestamp)
                                            true)
                                        false))
      (mount/start-with {#'last-fixed-kline-timestamp (:timestamp (last cutted-klines))}))
    (when (and (= -1 last-fixed-kline-timestamp-index)
               (<= diff-minutes config/API-MAX-KLINES-LENGTH))
      (log/error "diff minutes <" config/API-MAX-KLINES-LENGTH ", BUT NOT FOUND last kline timestamp:" last-fixed-kline-timestamp "in api data!"))
    (log-current-klines newest-kline id)))

(defn init-klines-watcher []
  (let [fixed-klines-data-path (:fixed-klines-data-path env)
        current-klines-data-path (:current-klines-data-path env)
        detail-data-path (:detail-data-path env)]
    (when-not (.exists (java.io.File. fixed-klines-data-path))
      (log/error "fixed klines data path NOT EXISTS!")
      (System/exit 1))
    (when-not (.exists (java.io.File. current-klines-data-path))
      (log/error "current klines data path NOT EXISTS!")
      (System/exit 1))
    (when-not (.exists (java.io.File. detail-data-path))
      (log/error "detail data path NOT EXISTS!")
      (System/exit 1))
    (mount/start-with {#'last-fixed-kline-timestamp (get-last-kline-timestamp-local fixed-klines-data-path)})
    (if (= last-fixed-kline-timestamp -1)
      (let [klines (models/api2klines (utils/get-kline "001" config/API-MAX-KLINES-LENGTH))
            newest-kline (last klines)
            fixed-klines (drop-last klines)
            last-fixed-kline (last fixed-klines)]
        (log-fixed-klines fixed-klines :new? true)
        (mount/start-with {#'last-fixed-kline-timestamp (:timestamp last-fixed-kline)}))))
  (log/info "finish init klines watcher."))

(defn klines-watcher []
  (let [id batch-id
        _ (mount/start-with {#'batch-id (inc batch-id)})]
  (klines-dealer id)
  (rd/watch-once id))
  (log/info "kline watcher once!"))
