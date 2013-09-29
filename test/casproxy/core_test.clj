(ns casproxy.core-test
  (:require [clojure.test :refer :all]
            [casproxy.core :refer :all]
            [clj-http.client :as client])
  )

(def cas-login-url "https://itbi.colo.elex.be:8443/cas/login")

(defn get-login-page []
  (client/get cas-login-url))

(deftest test-get-form-params
  (testing "cannot get form parameters from page"
    (is (< 0 (count (get-form-params (get-login-page)))))))

(deftest test-form-translation
  (testing "Test if the html form fields are properly translated"
    (let [fields (test-get-form-params)
          keys   (keys fields)]
      (is (contains? keys "username"))
      (is (contains? keys "password")))))
