(ns rocks.pho.btc.whac-a-mole.models
  (:require [rocks.pho.btc.whac-a-mole.utils :as utils]))

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
  "from kline object to a line log"
  [kline & {:keys [tag]
            :or {tag "C"}}]
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
    {:timestamp (long (nth cols 0))
     :open-price (double (nth cols 1))
     :highest-price (double (nth cols 2))
     :lowest-price (double (nth cols 3))
     :close-price (double (nth cols 4))
     :amount (double (nth cols 5))
     :tag (nth cols 6)}))

(defn log2klines
  "from a log file content to klines"
  [content]
  (let [lines (clojure.string/split-lines content)]
    (map #(line2kline %) lines)))
