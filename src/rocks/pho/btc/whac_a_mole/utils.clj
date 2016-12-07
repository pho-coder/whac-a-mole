(ns rocks.pho.btc.whac-a-mole.utils
 (:require [clj-http.client :as http-client]
           [clojure.data.json :as json]))

(defn format-datetime [kline-time]
  (let [datetime-str (str (.substring kline-time 0 4)
                          "-"
                          (.substring kline-time 4 6)
                          "-"
                          (.substring kline-time 6 8)
                          " "
                          (.substring kline-time 8 10)
                          ":"
                          (.substring kline-time 10 12)
                          ":"
                          (.substring kline-time 12 14)
                          "."
                          (.substring kline-time 14 17))]
    {:datetime datetime-str
     :ts (.getTime (java.sql.Timestamp/valueOf datetime-str))}))

(defn get-kline
  "get kline 001 005 ...
  https://github.com/huobiapi/API_Docs/wiki/REST-Interval"
  [type & length]
  (let [url (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")]
    (json/read-str (:body (if (nil? length)
                            (http-client/get url)
                            (http-client/get url {:query-params {"length" length}})))
                   :key-fn keyword)))
