(ns rocks.pho.btc.whac-a-mole.core
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            [com.jd.bdp.magpie.util.timer :as timer]

            [rocks.pho.btc.whac-a-mole.config :refer [env]]
            [rocks.pho.btc.whac-a-mole.watcher :as watcher]
            [rocks.pho.btc.whac-a-mole.tactics.realtime-detail :as rd])
  (:gen-class))

(mount/defstate klines-timer :start (timer/mk-timer)
                             :stop (when @(:active klines-timer)
                                     (timer/cancel-timer klines-timer)))

(defn check-klines-timer []
  (when-not @(:active klines-timer)
    (log/error "klines timer inactive!")
    (mount/stop #'klines-timer)
    (mount/start #'klines-timer)
    (rd/init-wallet)
    (timer/schedule-recurring klines-timer 1 5 watcher/klines-watcher)
    (log/info "restart klines timer!")))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn start-app [args]
  (doseq [component (-> args
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (log/info "data path:" (:btc-data-path env))
  (log/info "fixed klines data path:" (:fixed-klines-data-path env))
  (log/info "current klines data path:" (:current-klines-data-path env))
  ;  (watcher/init-klines-watcher)
  (rd/init-wallet)
  (timer/schedule-recurring klines-timer 1 5 watcher/klines-watcher)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (log/info "Hello, Whac A Mole!")
  (start-app args)
  (while true
    (Thread/sleep 2000)
    (check-klines-timer)))
