(ns casproxy.core-test
  (:require [clojure.test :refer :all]
            [casproxy.core :refer :all]
            [clj-http.client :as client] )
  (:use [ring.adapter.jetty :only (run-jetty)]
        [ring.middleware.session]
        [ring.middleware.cookies]
        [ring.middleware.params]
        [ring.util.response]
        [ring.util.request]
        [ring.util.codec]))

; # Testing the parsing of the login page
;
; Testing the parsing of the login page uses a static file
; snarfed from the CAS server using
;    cd resources
;    curl -O https://itbi.colo.elex.be:8443/cas/login
; and then exposing it using a temporary test server.e
;
; This approach avoids making assumptions about the login 
; page and to update it quickly should it ever change.
;
; Since I do not want to touch the page, I store the service 
; to redirect back to after the login in the session.
;
(def cas-login-url "http://localhost:13000/login")

(defn cas-handler[{method :request-method _session :session params :params}]
  "Simulate the interaction with the CAS server.
  On a get request we present a copy of the CAS login page which was snarfed with curl.
  On a post we redirect to the service passed as a parameter."
  (let [session (or _session {})
        service (or 
                  (params "service") 
                  (session :service))
        new-session (assoc session :service service)]
    (println "request: " method)
    (println "service: " service)
    (if (= :post method)
      (redirect-after-post (str service "?ticket=TGT-SampleTicket"))
      (-> (resource-response "/public/login")
          (assoc :session new-session)
          ))))    ; store the location given in the get 

(defn create-test-cas-server []
  (let [app (-> cas-handler 
                (wrap-cookies)
                (wrap-params)
                (wrap-session))]
    (run-jetty app {:host "localhost" :port 13000 :join? false}))) 


; # Simulated CAS protected servers
; 
; As long as there is no ticket in the session the server will redirect
; to the CAS server. 
;
;
(defn server-handler[request]
  "Simulate the interaction with the CAS server.
  On a get request we present a copy of the CAS login page which was snarfed with curl.
  On a post we redirect to the service passed as a parameter."
  (let [method (request :request-method)
        url    (request-url request)
        session (or (request :session) {})
        params  (or (request :params) {})
        ticket  (or 
                  (params "ticket") 
                  (session :ticket))
        new-session (assoc session :ticket ticket)]
    (println "Request URL : " url) 
    (println "Ticket : " ticket)
    (if (nil? ticket)
      (redirect (str cas-login-url "?" (str "service=" (url-encode url))))
      (-> (response "Hello, world.")
          (assoc :session new-session)))))     ; store the ticket given in the get 

(defn create-test-server []
  (let [app (-> server-handler 
                (wrap-cookies)
                (wrap-params)
                (wrap-session))]
    (run-jetty app {:host "localhost" :port 13001 :join? false}))) 

(defn ring-fixture [f]
  (let [cas-server (create-test-cas-server)
        server1 (create-test-server)]
    (f)
    (.stop cas-server)
    (.stop server1)))

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

(deftest test-server-login
  (testing "Test transparant access to CAS protected site"
    (let [response (client/get "http://localhost:13001/test")]
      (println response))))

