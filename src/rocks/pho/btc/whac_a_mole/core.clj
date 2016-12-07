(ns rocks.pho.btc.whac-a-mole.core
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]

            [rocks.pho.btc.whac-a-mole.config :refer [env]])
  (:gen-class))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn start-app [args]
  (doseq [component (-> args
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (log/debug env)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (log/info "Hello, World!")
  (start-app args))
