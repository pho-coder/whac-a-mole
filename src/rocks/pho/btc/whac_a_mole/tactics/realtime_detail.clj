(ns rocks.pho.btc.whac-a-mole.tactics.realtime-detail
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]

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
                                    :type ""
                                    :amount 0})

(mount/defstate wallet :start {:cny 0
                               :btc 0
                               :ts 0
                               :datetime ""})

(mount/defstate recent-points :start (list))

(mount/defstate last-top-point :start {:price 0
                                       :ts 0
                                       :datetime ""})

(defn log-detail
  [detail id]
  (let [ts (System/currentTimeMillis)
        datetime (utils/get-readable-time ts)
        price (:p-new detail)
        format "YYYY-MM-dd_HH"
        path (:detail-data-path env)
        file-name (utils/get-readable-time (:timestamp detail) format)
        file-path (str path "/" file-name)]
    (spit file-path (str id "," (models/detail2line detail) "\n") :append true)
    (when (>= price (:price last-top-point))
      (mount/start-with {#'last-top-point {:price price
                                           :ts ts
                                           :datetime datetime}}))
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

(defn init-wallet
  []
  (let [account-info-str (utils/get-account-info (:server-url env) (:secret-code env))]
    (if (:success? account-info-str)
      (let [account-info (json/read-str (:info account-info-str)
                                        :key-fn keyword)
            ts (System/currentTimeMillis)]
        (mount/start-with {#'wallet {:cny (Double/parseDouble (:available_cny_display account-info))
                                     :btc (Double/parseDouble (:available_btc_display account-info))
                                     :ts ts
                                     :datetime (utils/get-readable-time ts)}})
        (log/info wallet))
      (do (log/error "init wallet ERROR:" account-info-str)
          (System/exit 1)))))

(defn check-recent-points
  []
  (let [times (:check-times env)
        diff 2
        points recent-points]
    (if (< (.size points) times)
      (do (log/info "recent data is not enough:" times)
          "")
      (let [data (take times points)
            re (map (fn [o]
                      (let [asks-amount (:price2-asks-amount o)
                            bids-amount (:price2-bids-amount o)]
                        (if (>= (/ (double asks-amount)
                                   (double bids-amount))
                                diff)
                          "ask"
                          (if (>= (/ (double bids-amount)
                                     (double asks-amount))
                                  diff)
                            "bid"
                            ""))))
                    data)
            dealed (reduce (fn [t o]
                             (if (= t o)
                               t
                               "")) (first re) re)]
        (log/info "recent data:" data)
        (log/info "recent re:" re)
        (when (or (and (= dealed "ask")
                       (< (:new-price (first data))
                          (- (:new-price (second data)) 2)))
                  (and (= dealed "bid")
                       (>= (:new-price (first data))
                           (:new-price (second data)))))
          (log/info "point:" dealed)
          ;; (if (= dealed "bid")
          ;;   (if (not= "bid" (:type trade-point))
          ;;     (init-wallet)))
          ;; (if (= dealed "ask")
          ;;   (if (not= "ask" (:type trade-point))
          ;;     (init-wallet)))
          dealed)))))

(defn buy
  [cny new-price]
  (let [ts (System/currentTimeMillis)
        datetime (utils/get-readable-time ts)
        re (utils/buy-market (:server-url env)
                             (:secret-code env)
                             cny)
        info (json/read-str (:info re)
                            :key-fn keyword)]
    (if (:success? re)
      (log/info "buy:" cny "info:" info)
      (log/error "buy:" cny "error:" re))
    (init-wallet)
    (when (:success? re)
      (mount/start-with {#'trade-point {:price new-price
                                        :ts ts
                                        :datetime datetime
                                        :type "bid"
                                        :amount (:btc wallet)}})
      (mount/start-with {#'last-top-point {:price new-price
                                           :ts ts
                                           :datetime datetime}}))))

(defn sell
  [btc new-price]
  (let [ts (System/currentTimeMillis)
        datetime (utils/get-readable-time ts)
        re (utils/sell-market (:server-url env)
                              (:secret-code env)
                              btc)
        info (json/read-str (:info re)
                            :key-fn keyword)]
    (if (:success? re)
      (log/info "sell:" btc "info:" info)
      (log/error "sell:" btc "error:" re))
    (init-wallet)
    (when (:success? re)
      (log/info "maybe got diff:" (- new-price
                                     (:price trade-point)))
      (mount/start-with {#'trade-point {:price new-price
                                        :ts ts
                                        :datetime datetime
                                        :type "ask"
                                        :amount btc}}))
    (when (and (= (:code info) 63)
               (<= btc 0.001))
      (log/error "sell" btc "below than 0.001, so buy some then sell")
      (buy 10 new-price)
      (sell (:btc wallet) new-price))))

(defn watch-once
  [id]
  (let [detail (models/api2detail (utils/get-realtime-detail))
        max-amount-buy (:max-amount-buy detail)
        max-amount-sell (:max-amount-sell detail)
        bid-amount (:bid-amount detail)
        ask-amount (:ask-amount detail)
        depth (models/api2depth (utils/get-depth))
        ts (System/currentTimeMillis)
        datetime (utils/get-readable-time ts)]
    (log-detail detail id)
    (log-depth depth id)
    (log/info "last top point:" last-top-point)
    (mount/start-with {#'recent-points (conj recent-points {:new-price (:p-new detail)
                                                            :asksed-amount ask-amount
                                                            :bidsed-amount bid-amount
                                                            :price1-asks-amount (:price1-asks-amount depth)
                                                            :price1-bids-amount (:price1-bids-amount depth)
                                                            :price2-asks-amount (:price2-asks-amount depth)
                                                            :price2-bids-amount (:price2-bids-amount depth)
                                                            :asks-amount (:asks-amount depth)
                                                            :bids-amount (:bids-amount depth)
                                                            :ts ts
                                                            :datetime datetime})})
    (while (> (.size recent-points) 10)
      (mount/start-with {#'recent-points (drop-last recent-points)}))

    (let [re (check-recent-points)
          btc (:btc wallet)
          cny (:cny wallet)]
      (when (and (= re "bid")
                 (<= btc 0))
        (log/info "CAN BUY")
        (buy (int cny) (:p-new detail)))
      (when (and (= re "ask")
                 (> btc 0))
        (log/info "CAN SELL")
        (sell btc (:p-new detail))))
    
    (when (> (:btc wallet) 0)
      (let [price (:p-new detail)
            diff-top (- (:price last-top-point) price)
            diff-top-rate (/ (* diff-top 1000)
                             price)
            sell-rate (:must-sell-rate env)]
        (when (> diff-top-rate
                 sell-rate)
          (log/info "MUST SELL")
          (log/info "diff top more than " sell-rate " thousandth.diff price:" diff-top "last top point:" last-top-point)
          (sell (:btc wallet) (:p-new detail)))))
    
    (when (> (- (System/currentTimeMillis)
                (:ts watching-point))
             (* 5 60 1000))
      (mount/start-with {#'watching-point {:price (:p-new detail)
                                           :ts ts
                                           :datetime datetime}})
      (log/info "update watching point:" watching-point))

    (when (> (- (System/currentTimeMillis)
                (:ts wallet))
             (* 1 60 1000))
      (init-wallet))))
