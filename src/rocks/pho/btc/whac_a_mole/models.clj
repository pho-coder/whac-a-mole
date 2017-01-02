(ns rocks.pho.btc.whac-a-mole.models
  (:require [clojure.tools.logging :as log]
            [rocks.pho.btc.whac-a-mole.utils :as utils]))

(defn list2kline
  "from one line api data
  timestamp, open-price, highest-price, lowest-price, close-price, amount"
  [a-vec]
  (if-not (= 6 (.size a-vec))
    (throw (Exception. (str a-vec "'s size is not 6!")))
    {:timestamp (:timestamp (utils/format-datetime (nth a-vec 0)))
     :open-price (double (nth a-vec 1))
     :highest-price (double (nth a-vec 2))
     :lowest-price (double (nth a-vec 3))
     :close-price (double (nth a-vec 4))
     :amount (double (nth a-vec 5))}))

(defn api2klines
  "from api data to klines list"
  [a-list]
  (map #(list2kline %) a-list))

(defn kline2log
  "from kline object to a line log
   tag: N(ew), S(uccessive), C(urrent)"
  [kline tag]
  (str (:timestamp kline) ","
       (:open-price kline) ","
       (:highest-price kline) ","
       (:lowest-price kline) ","
       (:close-price kline) ","
       (:amount kline) ","
       tag))

(defn line2kline
  "from a kline log line to kline"
  [a-line]
  (let [cols (clojure.string/split a-line #",")]
    {:timestamp (Long/parseLong (nth cols 0))
     :open-price (Double/parseDouble (nth cols 1))
     :highest-price (Double/parseDouble (nth cols 2))
     :lowest-price (Double/parseDouble (nth cols 3))
     :close-price (Double/parseDouble (nth cols 4))
     :amount (Double/parseDouble (nth cols 5))
     :tag (nth cols 6)}))

(defn log2klines
  "from a log file content to klines"
  [content]
  (let [lines (clojure.string/split-lines content)]
    (map #(line2kline %) lines)))

(defn api2detail
  "from api data to detail map"
  [a-map]
  (let [buys (reverse (:buys a-map))
        first-buy (first buys)
        last-buy (last buys)
        max-amount-buy (reduce (fn [t o] (if (> (:amount o)
                                                (:amount t))
                                           o
                                           t))
                               first-buy
                               buys)
        sells (:sells a-map)
        first-sell (first sells)
        last-sell (last sells)
        max-amount-sell (reduce (fn [t o] (if (> (:amount o)
                                                 (:amount t))
                                            o
                                            t))
                                first-sell
                                sells)
        trades (:trades a-map)
        first-trade (first trades)
        last-trade (last trades)
        max-amount-trade (reduce (fn [t o] (if (> (:amount o)
                                                  (:amount t))
                                             o
                                             t))
                                 first-trade
                                 trades)
        trades-type-amount (reduce (fn [t o] (case (:en_type o)
                                               "bid" (update t :bid-amount + (bigdec (:amount o)))
                                               "ask" (update t :ask-amount + (bigdec (:amount o)))))
                                   {:bid-amount 0 :ask-amount 0}
                                   trades)]
    {:timestamp (System/currentTimeMillis)
     :amount (:amount a-map)
     :p-last (:p_last a-map)
     :p-low (:p_low a-map)
     :level (:level a-map)
     :p-new (:p_new a-map)
     :total (:total a-map)
     :p-open (:p_open a-map)
     :p-high (:p_high a-map)
     :buys buys
     :first-buy first-buy
     :last-buy last-buy
     :max-amount-buy max-amount-buy
     :sells sells
     :first-sell first-sell
     :last-sell last-sell
     :max-amount-sell max-amount-sell
     :trades trades
     :bid-amount (:bid-amount trades-type-amount)
     :ask-amount (:ask-amount trades-type-amount)}))

(defn detail2line
  "from detail object to a line log"
  [detail]
  (str (:timestamp detail) ","
       (:p-new detail) ","
       (:amount (:max-amount-buy detail)) ","
       (:price (:max-amount-buy detail)) ","
       (:amount (:max-amount-sell detail)) ","
       (:price (:max-amount-sell detail)) ","
       (:bid-amount detail) ","
       (:ask-amount detail)))

(defn line2detail
  "from a detail line to detail object"
  [a-line]
  (let [cols (clojure.string/split a-line #",")]
    {:timestamp (Long/parseLong (nth cols 0))
     :p-new (Double/parseDouble (nth cols 1))
     :max-amount-buy {:amount (Double/parseDouble (nth cols 2))
                      :price (Double/parseDouble (nth cols 3))}
     :max-amount-sell {:amount (Double/parseDouble (nth cols 4))
                       :price (Double/parseDouble (nth cols 5))}
     :bid-amount (nth cols 6)
     :ask-amount (nth cols 7)}))

(defn api2depth
  "thousandth; price1 means one in a thousand"
  [a-map]
  (let [asks (:asks a-map)
        bids (:bids a-map)
        first-ask-price (bigdec (first (first asks)))
        ask-price1 (+ first-ask-price (/ first-ask-price 1000))
        ask-price2 (+ first-ask-price (* (/ first-ask-price 1000) 2))
        last-ask-price (bigdec (first (last asks)))
        first-bid-price (bigdec (first (first bids)))
        bid-price1 (- first-bid-price (/ first-bid-price 1000))
        bid-price2 (- first-bid-price (* (/ first-bid-price 1000) 2))
        last-bid-price (bigdec (first (last bids)))
        [price1-asks-amount
         price2-asks-amount
         asks-amount] (reduce (fn [t o]
                                (let [price1-amount-inc (if (<= (first o) ask-price1)
                                                          (bigdec (second o))
                                                          0)
                                      price2-amount-inc (if (<= (first o) ask-price2)
                                                          (bigdec (second o))
                                                          0)]
                                  (update (update (update t 0 + price1-amount-inc)
                                                  1 + price2-amount-inc)
                                          2 + (bigdec (second o)))))
                              [0 0 0]
                              asks)
        [price1-bids-amount
         price2-bids-amount
         bids-amount] (reduce (fn [t o]
                                (let [price1-amount-inc (if (>= (first o) bid-price1)
                                                          (bigdec (second o))
                                                          0)
                                      price2-amount-inc (if (>= (first o) bid-price2)
                                                          (bigdec (second o))
                                                          0)]
                                  (update (update (update t 0 + price1-amount-inc)
                                                  1 + price2-amount-inc)
                                          2 + (bigdec (second o)))))
                              [0 0 0]
                              bids)]
    {:timestamp (System/currentTimeMillis)
     :first-ask-price first-ask-price
     :last-ask-price last-ask-price
     :price1-asks-amount price1-asks-amount
     :price2-asks-amount price2-asks-amount
     :asks-amount asks-amount
     :first-bid-price first-bid-price
     :last-bid-price last-bid-price
     :price1-bids-amount price1-bids-amount
     :price2-bids-amount price2-bids-amount
     :bids-amount bids-amount}))

(defn depth2line
  "from depth object to a line log"
  [depth]
  (str (:timestamp depth) ","
       (:first-ask-price depth) ","
       (:last-ask-price depth) ","
       (:price1-asks-amount depth) ","
       (:price2-asks-amount depth) ","
       (:asks-amount depth) ","
       (:first-bid-price depth) ","
       (:last-bid-price depth) ","
       (:price1-bid-amount depth) ","
       (:price2-bid-amount depth) ","
       (:bids-amount depth)))

(defn line2depth
  "from a depth line to depth object"
  [a-line]
  (let [cols (clojure.string/split a-line #",")]
    {:timestamp (Long/parseLong (nth cols 0))
     :first-ask-price (bigdec (nth cols 1))
     :last-ask-price (bigdec (nth cols 2))
     :price1-asks-amount (bigdec (nth cols 3))
     :pirce2-asks-amount (bigdec (nth cols 4))
     :asks-amount (bigdec (nth cols 5))
     :first-bid-price (bigdec (nth cols 6))
     :last-bid-price (bigdec (nth cols 7))
     :price1-bid-amount (bigdec (nth cols 8))
     :price2-bid-amount (bigdec (nth cols 9))
     :bids-amount (bigdec (nth cols 10))}))
