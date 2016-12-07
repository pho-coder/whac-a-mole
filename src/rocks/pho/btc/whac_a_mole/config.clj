(ns rocks.pho.btc.whac-a-mole.config
  (:require [mount.core :refer [defstate]]
            [cprop.source :as source]))

(defstate env :start (source/from-env))
