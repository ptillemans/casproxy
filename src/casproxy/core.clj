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
  (client/get url {:cookie-store cookie-store
                   :force-redirects true
                   :throw-exceptions false}))

(defn c-post [url form-params]
  (client/post url {:form-params form-params
                    :cookie-store cookie-store
                    :force-redirects true
                    :throw-exceptions false}))

(defn c-req [req]
  (client/request (assoc req
                         :cookie-store cookie-store
                         :throw-exceptions false
                         :force-redirects true)))


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
      (let [resp  (c-post url filled-form)
            url (get-redirected-url resp)]
        (pprint (str "  Following to payload url: " url))
        (let  [payload-response (c-get url)]
          (pprint (str "  Status:" (:status payload-response)))
          (pprint (str " Body: " (:body payload-response)))
          payload-response
          )))))

(defn do-request [req]
  (pprint (str "Starting request : " (req :uri)))
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

(defn slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (clojure.java.io/copy (clojure.java.io/input-stream x) out)
    (.toByteArray out)))

(defn handler [req]
  (let [body (slurp-bytes (:body req))]
    (do-request (create-proxy-request (assoc req :body body)))))

(defn run-server []
  (let [spagobi-url (str (scheme) "://" (server-name) ":" (server-port) "/SpagoBITalendEngine/EngineInfoService")]
    (pprint spagobi-url)
    (let [resp (c-get spagobi-url)]
      (pprint resp)
      (if (is-login-url? resp)
        (login resp)
        resp)))
  (run-jetty handler {:port 3000 :host "localhost"}))

(defn -main [& args]
  (run-server))
