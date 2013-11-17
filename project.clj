(defproject casproxy "0.1.1-SNAPSHOT"
  :description "Connect transparently to a CAS protected server through a local server."
  :url "https://github.com/ptillemans/casproxy"
  :license {:name "GNU Public License"
            :url "http://www.gnu.org/licenses/gpl-2.0.html"}
  :deploy-repositories [["snapshots"
                         {:url "http://nexus.colo.elex.be:8081/nexus/content/repositories/snapshots"
                          :creds :gpg}]
                        ["releases"
                         {:url "http://nexus.colo.elex.be:8081/nexus/content/repositories/releases"
                          :creds :gpg}]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-http "0.7.7"]
                 [ring/ring-core "1.2.0"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [enlive "1.1.4"]]
  :plugins [[quickie "0.2.4"]
            [lein-marginalia "0.7.1"]]
  :main casproxy.core)
