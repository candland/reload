(defproject net.candland/reload "0.0.1-SNAPSHOT"

  :description "Used to reload a running JAR and minimize down time."
  :url "https://github.com/candland/reload"

  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [beckon "0.1.1"]]

  :deploy-repositories [["releases" :clojars]]

  :profiles {:dev {:source-paths ["dev"]} })
