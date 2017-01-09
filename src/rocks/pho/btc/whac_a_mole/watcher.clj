(ns rocks.pho.btc.whac-a-mole.watcher
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            
            [rocks.pho.btc.whac-a-mole.utils :as utils]
            [rocks.pho.btc.whac-a-mole.config :refer [env]]
            [rocks.pho.btc.whac-a-mole.models :as models]
            [rocks.pho.btc.whac-a-mole.config :as config]))

;; id for taging data batch : 18 bit
;; timestamp(second:10 bit) and 8 bit for (* 60 60 24 365) : 31536000
(mount/defstate batch-id :start (* 100000000 (quot (System/currentTimeMillis)
                                                   1000)))

(mount/defstate trade-point :start {:price 0
                                    :ts 0
                                    :datetime ""
                                    :type ""
                                    :amount 0
                                    :id 0
                                    :checked? true})

(mount/defstate wallet :start {:cny 0
                               :btc 0
                               :ts 0
                               :datetime ""})

(mount/defstate recent-points :start (list))

(mount/defstate last-top-point :start {:price 0
                                       :ts 0
                                       :datetime ""})

(mount/defstate recent-orders :start (list))

(mount/defstate orders :start (list))

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

(defn reset-wallet
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
      (do (log/error "reset wallet ERROR:" account-info-str)
          (System/exit 1)))))

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
    (reset-wallet)
    (when (:success? re)
      (mount/start-with {#'trade-point {:price new-price
                                        :ts ts
                                        :datetime datetime
                                        :type "bid"
                                        :amount (:btc wallet)
                                        :id (:id info)
                                        :checked? false}})
      (mount/start-with {#'last-top-point {:price new-price
                                           :ts ts
                                           :datetime datetime}})
      (mount/start-with {#'recent-orders (conj recent-orders trade-point)}))))

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
    (reset-wallet)
    (when (:success? re)
      (log/info "maybe got diff:" (- new-price
                                     (:price trade-point)))
      (mount/start-with {#'trade-point {:price new-price
                                        :ts ts
                                        :datetime datetime
                                        :type "ask"
                                        :amount btc
                                        :id (:id info)
                                        :checked? false}})
      (mount/start-with {#'recent-orders (conj recent-orders trade-point)}))
    (when (and (= (:code info) 63)
               (<= btc 0.001))
      (log/warn "sell" btc "below than 0.001, so buy some then sell")
      (buy 10 new-price)
      (sell (:btc wallet) new-price))))

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
        (log/debug "recent data:" data)
        (log/info "recent re:" re)
        (when (or (and (= dealed "ask")
                       (< (:new-price (first data))
                          (- (:new-price (second data)) 2)))
                  (and (= dealed "bid")
                       (>= (:new-price (first data))
                           (:new-price (second data)))))
          (log/info "point:" dealed)
          dealed)))))

(defn init-watcher []
  (let [detail-data-path (:detail-data-path env)
        depth-data-path (:depth-data-path env)]
    (when-not (.exists (java.io.File. detail-data-path))
      (log/error "detail data path NOT EXISTS!")
      (System/exit 1))
    (when-not (.exists (java.io.File. depth-data-path))
      (log/error "depth data path NOT EXISTS!")
      (System/exit 1)))
  (log/info "finish init watcher."))

(defn watch-once []
  (let [detail (models/api2detail (utils/get-realtime-detail))
        depth (models/api2depth (utils/get-depth))
        id batch-id
        _ (mount/start-with {#'batch-id (inc batch-id)})
        ts (System/currentTimeMillis)
        datetime (utils/get-readable-time ts)]
    (log-detail detail id)
    (log-depth depth id)
    (log/info "last top point:" last-top-point)

    ;; deal recent points
    (mount/start-with {#'recent-points (conj recent-points {:new-price (:p-new detail)
                                                            :asksed-amount (:ask-amount detail)
                                                            :bidsed-amount (:bid-amount detail)
                                                            :price1-asks-amount (:price1-asks-amount depth)
                                                            :price1-bids-amount (:price1-bids-amount depth)
                                                            :price2-asks-amount (:price2-asks-amount depth)
                                                            :price2-bids-amount (:price2-bids-amount depth)
                                                            :asks-amount (:asks-amount depth)
                                                            :bids-amount (:bids-amount depth)
                                                            :ts ts
                                                            :datetime datetime})})
    (while (> (.size recent-points) 100)
      (mount/start-with {#'recent-points (drop-last recent-points)}))

    ;; check recent points buy or sell
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

    ;; check must sell thousandth
    (when (> (:btc wallet) 0)
      (let [ts (System/currentTimeMillis)
            price (:p-new detail)
            diff-top (- (:price last-top-point) price)
            diff-time (/ (- ts (:ts last-top-point))
                         1000)
            diff-top-rate (/ (* diff-top 1000)
                             price)
            must-sell-rate (:must-sell-rate env)
            ;; cut must-sell-rate 50 quot, delay time no more than 52s
            sell-rate (* must-sell-rate (/ (- 52 diff-time) 50))]
        (when (> diff-top-rate
                 sell-rate)
          (log/info "MUST SELL")
          (log/info (str "\ndiff top more than " sell-rate "(" (with-precision 4 (bigdec sell-rate)) ") thousandth.")
                    "\ndiff time:" diff-time "(" (with-precision 4 (bigdec diff-time)) ")"
                    "\ndiff price:" diff-top "(" (with-precision 4 (bigdec diff-top)) ")"
                    "\nlast top point:" last-top-point)
          (sell (:btc wallet) (:p-new detail)))))

    ;; check wallet
    (when (and (< (:cny wallet) 10)
               (< (:btc wallet) 0.001))
      (reset-wallet))
    (log/info "wallet:" wallet))

  (let [name (.getName (java.lang.management.ManagementFactory/getRuntimeMXBean))
        pid (first (clojure.string/split name #"@"))]
    (spit (:watcher-pid-file env) pid))
  (log/info "watch once!\n\n\n"))
