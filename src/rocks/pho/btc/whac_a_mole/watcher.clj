(ns rocks.pho.btc.whac-a-mole.watcher
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            
            [rocks.pho.btc.whac-a-mole.utils :as utils]
            [rocks.pho.btc.whac-a-mole.config :refer [env]]
            [rocks.pho.btc.whac-a-mole.models :as models]
            [rocks.pho.btc.whac-a-mole.config :as config]))

(mount/defstate last-kline-timestamp :start -1)

;; klines data in one klines data path;
;; file name YYYY-MM-dd_HH
;; file content: 
;; functions used below
(defn check-klines-dir [klines-data-path]
  (let [file (clojure.java.io/as-file klines-data-path)]
    (.exists file)))

(defn get-all-klines-files [klines-data-path]
  (let [file (clojure.java.io/as-file klines-data-path)
        klines-files (.list file)]
    klines-files))

(defn log-klines
  "if new write N(ew) at first line tail else write C(ontinue)"
  [klines-list & {:keys [new?]
                  :or {new? false}}]
  (let [format "YYYY-MM-dd_HH"
        path (:klines-data-path env)]
    (let [first-one (first klines-list)
          file-name (utils/get-readable-time (:timestamp first-one) format)
          file-path (str path "/" file-name)]
      (if new?
        (spit file-path (str (models/kline2log first-one :tag "N") "\n") :append true)
        (spit file-path (str (models/kline2log first-one) "\n") :append true))
      (doall (doseq [one (rest klines-list)]
               (let [file-name (utils/get-readable-time (:timestamp one) format)
                     file-path (str path "/" file-name)]
                 (spit file-path (str (models/kline2log one) "\n") :append true)))))))

(defn get-last-kline-timestamp [path]
  (let [files (.list (clojure.java.io/as-file path))
        max-file (reduce (fn [m o]
                           (if (> (compare o m) 0)
                             o
                             m))
                         "" files)]
    (if (= max-file "")
      -1
      (:timestamp (last (models/log2klines (slurp (str path "/" max-file))))))))

(defn init-klines-watcher []
  (let [klines-data-path (:klines-data-path env)]
    (when-not (.exists (java.io.File. klines-data-path))
      (log/error "klines data path NOT EXISTS!")
      (System/exit 1))
    (let [last-timestamp (get-last-kline-timestamp klines-data-path)]
      (if (= last-timestamp -1)
        (let [klines (models/api2klines (utils/get-kline "001" config/API-MAX-KLINES-LENGTH))
              newest-kline (last klines)
              fixed-klines (drop-last klines)
              last-fixed-kline (last fixed-klines)]
          (log-klines fixed-klines :new? true)
          (mount/start-with {#'last-kline-timestamp (:timestamp last-fixed-kline)}))))))

(defn klines-watcher []
  (let [klines-data-path (clojure.java.io/as-file (:klines-data-path env))
        klines-data-now (utils/get-kline "001" 10)
        klines-files (.list klines-data-path)])
  (log/info "kline watcher once!"))