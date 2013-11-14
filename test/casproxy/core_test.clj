(ns casproxy.core-test
  (:require [clojure.test :refer :all]
            [casproxy.core :refer :all]
            [clj-http.client :as client] )
  (:use [ring.adapter.jetty :only (run-jetty)]
        [ring.middleware.session]
        [ring.middleware.cookies]
        [ring.middleware.params]
        [ring.util.response]))

;
; Testing the parsing of the login page uses a static file
; snarfed from the CAS server using
;    cd resources
;    curl -O https://itbi.colo.elex.be:8443/cas/login
; and then exposing it using a temporary test server
(def cas-login-url "http://localhost:13000/login")

(defn cas-handler[{method :request-method _session :session params :params}]
  "Simulate the interaction with the CAS server.
  On a get request we present a copy of the CAS login page which was snarfed with curl.
  On a post we redirect to the service passed as a parameter."
  (let [session (or _session {})
        service (or 
                  (params "service") 
                  (session :service))]
    (println "Service: " service)
    (println "Session: " session)
    (if (= :post method)
      (redirect-after-post service)
      (-> (resource-response "/public/login")
          (assoc :session 
                 (assoc session 
                   :service service))))))     ; store the location given in the get 

(defn create-test-cas-server []
  (let [app (-> cas-handler 
                (wrap-cookies)
                (wrap-params)
                (wrap-session))]
    (run-jetty app {:host "localhost" :port 13000 :join? false}))) 

(defn ring-fixture [f]
  (let [server (create-test-cas-server)]
    (f)
    (.stop server)))

(use-fixtures :once ring-fixture)

(defn get-login-page []
  (client/get cas-login-url))

(deftest test-get-form-params
  (testing "cannot get form parameters from page"
    (is (< 0 (count (get-form-params (get-login-page)))))))

(deftest test-form-translation
  (testing "Test if the html form fields are properly translated"
    (let [fields (get-form-params (get-login-page))]
      (is (contains? fields "username"))
      (is (contains? fields "password")))))
