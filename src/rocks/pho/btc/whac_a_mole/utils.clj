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
     :timestamp (.getTime (java.sql.Timestamp/valueOf datetime-str))}))

(defn get-readable-time
  "get readable time default format yyyy-MM-dd HH:mm:ss"
  ([long-time fm]
   (let [ts (java.sql.Timestamp. (if (= (.length (str long-time)) 10)
                                   (* long-time 1000)
                                   long-time))
         df (java.text.SimpleDateFormat. fm)]
     (.format df ts)))
  ([long-time]
   (get-readable-time long-time "yyyy-MM-dd HH:mm:ss.SSS")))

(defn get-kline
  "get kline 001 005 ...
  https://github.com/huobiapi/API_Docs/wiki/REST-Interval"
  [type & length]
  (let [url (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")]
    (json/read-str (:body (if (nil? length)
                            (http-client/get url)
                            (http-client/get url {:query-params {"length" length}})))
                   :key-fn keyword)))


