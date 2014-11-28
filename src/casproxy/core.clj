(ns casproxy.core
  (:require [clj-http.client :as client]
            [net.cgrand.enlive-html :as html])
  (:use ring.adapter.jetty)
  (:use clojure.pprint)
  (:require [clojure.java.io :as io])
  (:gen-class))

(defn- load-props- [file-name]
  "Load the given properties file and return it as a map."
  (with-open [^java.io.Reader reader (clojure.java.io/reader file-name)]
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (read-string v)])))))

; wrap the config loading to memoize the result.
(def load-props (memoize load-props-))

(defn config [key]
  "Get a configuration parameter"
  (let [props (load-props (str (System/getProperty "user.home") "/.casproxy.properties"))]
    (props key)))

;; some convenience functions to access the config parameters
(defn login-url [] (config :login-url ))
(defn username  [] (config :username))
(defn password  [] (config :password))
(defn scheme [] (config :scheme))
(defn server-name [] (config :server-name))
(defn server-port [] (config :server-port))

;; clj-http.client function wrappers.
;; concentrate calls here so http client calls are not strewn over the file.
;; all files use the same cookie-store, so be very careful when using with
;; multiple simultaneous conversations.

(def cookie-store (clj-http.cookies/cookie-store))

(defn c-get [url]
  (client/get url {:cookie-store cookie-store}))

(defn c-post [url form-params]
  (client/post url {:form-params form-params
                    :cookie-store cookie-store}))

(defn c-req [req]
  (client/request (assoc req :cookie-store cookie-store)))




(defn get-redirected-url [resp]
  "return the url of the page which was returned in the redirects"
  (last (:trace-redirects resp))
)

(defn is-login-url? [resp]
  "Return true when the CAS login page is returned"
  (let [redirect (get-redirected-url resp)]
    (and redirect (.startsWith redirect (login-url)))))


(defn get-form-params [resp]
  "Return a map with form fields name-value pairs from the page in the resp."
  (let [body (html/html-snippet (:body resp))
        inputs (html/select body [:form :input])]
    (reduce merge
     (map
      #(let [attr (:attrs %)] {(:name attr) (:value attr)})
      inputs))))

(defn login [resp]
  "fill in the login form and submit."
  (let [url (get-redirected-url resp)]
    (pprint "Logging in to CAS\n" )
    (let [login-page (c-get url)
          orig-form  (get-form-params login-page)
          filled-form (assoc orig-form "username" (username) "password" (password))]
      (pprint "  Submitting Form:")
      (let [resp  (c-post url filled-form)]
        (pprint "  Following to payload.")
        (c-get ((resp :headers) "location"))))))


(defn do-request [req]
  (pprint (str "Starting request : " (req :uri)))
  ;(pprint req)
  (let [resp (c-req req )]
    (pprint (str " Status: " (:status resp)))
    resp))

(defn create-proxy-request [req]
  "turn request into a request to the real server"
  (assoc req
    :scheme (scheme)
    :server-name (server-name)
    :server-port (server-port)
    :headers (dissoc (:headers req) "content-length" "host")))

(defn handler [req]
  (let [resp (do-request (create-proxy-request req))
        status (:status resp)]
;    (pprint resp)
    (if (and (is-login-url? resp))
      (login resp)
      resp)))

(defn run-server []
  (run-jetty handler {:port 3000 :host "localhost"}))

(defn -main [& args]
  (run-server))
