(ns rocks.pho.btc.whac-a-mole.core
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            [com.jd.bdp.magpie.util.timer :as timer]

            [rocks.pho.btc.whac-a-mole.config :refer [env]]
            [rocks.pho.btc.whac-a-mole.watcher :as watcher])
  (:gen-class))

(mount/defstate watcher-timer :start (timer/mk-timer)
                              :stop (when @(:active watcher-timer)
                                      (timer/cancel-timer watcher-timer)))

(defn check-watcher-timer []
  (when-not @(:active watcher-timer)
    (log/error "watcher timer inactive!")
    (mount/stop #'watcher-timer)
    (mount/start #'watcher-timer)
    (watcher/init-wallet)
    (timer/schedule-recurring watcher-timer 1 5 watcher/watch-once)
    (log/info "restart watcher timer!")))

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
  (watcher/init-wallet)
  (timer/schedule-recurring watcher-timer 1 5 watcher/watch-once)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (log/info "Hello, okcoin, Whac A Mole!")
  (start-app args)
  (while true
    (Thread/sleep 2000)
    (check-watcher-timer)))
