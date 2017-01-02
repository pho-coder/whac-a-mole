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

(defn find-kline-timestamp
  "return -1 : not found
           n : find at index n"
  [klines ts]
  (loop [index 0]
    (if (>= index (.size klines))
      -1
      (if (= (:timestamp (nth klines index)) ts)
        index
        (recur (inc index))))))

(defn get-realtime-detail
  []
  (let [url "http://api.huobi.com/staticmarket/detail_btc_json.js"]
    (json/read-str (:body (http-client/get url))
                   :key-fn keyword)))

(defn get-depth
  []
  (let [url "http://api.huobi.com/staticmarket/depth_btc_json.js"]
    (json/read-str (:body (http-client/get url))
                   :key-fn keyword)))

(defn buy-market
  "buy by my server"
  [url code amount]
  (let [re (json/read-str (:body (http-client/post url
                                                   {:content-type :json
                                                    :body (str "{\"code\":" code
                                                               ",\"amount\":" amount
                                                               "}")}))
                          :key-fn keyword)]
    re))

(defn sell-market
  "sell by my server"
  [url code amount]
  (let [re (json/read-str (:body (http-client/post url
                                                   {:content-type :json
                                                    :body (str "{\"code\":" code
                                                               ",\"amount\":" amount
                                                               "}")}))
                          :key-fn keyword)]
    re))
