(ns rocks.pho.btc.whac-a-mole.tactics.realtime-detail
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]

            [rocks.pho.btc.whac-a-mole.models :as models]
            [rocks.pho.btc.whac-a-mole.utils :as utils]
            [rocks.pho.btc.whac-a-mole.config :refer [env]]))

(mount/defstate watching-point :start {:price 0
                                       :ts 0
                                       :datetime ""
                                       :type ""})

(mount/defstate trade-point :start {:price 0
                                    :ts 0
                                    :datetime ""
                                    :type ""})

(defn log-detail
  [detail id]
  (let [format "YYYY-MM-dd_HH"
        path (:detail-data-path env)
        file-name (utils/get-readable-time (:timestamp detail) format)
        file-path (str path "/" file-name)]
    (spit file-path (str id "," (models/detail2line detail) "\n") :append true)
    (log/info "log one detail - new price:" (:p-new detail))))

(defn log-depth
  [depth id]
  (let [format "YYYY-MM-dd_HH"
        path (:depth-data-path env)
        file-name (utils/get-readable-time (:timestamp depth) format)
        file-path (str path "/" file-name)]
    (spit file-path (str id "," (models/depth2line depth) "\n") :append true)
    (log/info "log one depth:\n"
              (str "asks-amount:\t\t"
                   (:asks-amount depth) "\n"
                   " bids-amount:\t\t" (:bids-amount depth) "\n"
                   " price1-asks-amount:\t" (:price1-asks-amount depth) "\n"
                   " price1-bids-amount:\t" (:price1-bids-amount depth) "\n"
                   " price2-asks-amount:\t" (:price2-asks-amount depth) "\n"
                   " price2-bids-amount:\t" (:price2-bids-amount depth)))))

(defn watch-once
  [id]
  (let [detail (models/api2detail (utils/get-realtime-detail))
        ts (System/currentTimeMillis)
        datetime (utils/get-readable-time ts)
        max-amount-buy (:max-amount-buy detail)
        max-amount-sell (:max-amount-sell detail)
        bid-amount (:bid-amount detail)
        ask-amount (:ask-amount detail)
        depth (models/api2depth (utils/get-depth))]
    (log-detail detail id)
    (log-depth depth id)
    (when (> (- (System/currentTimeMillis)
                (:ts watching-point))
             (* 5 60 1000))
      (mount/start-with {#'watching-point {:price (:p-new detail)
                                           :ts ts
                                           :datetime datetime}})
      (log/info "update watching point:" watching-point))))
