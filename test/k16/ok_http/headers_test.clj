(ns k16.ok-http.headers-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [k16.ok-http.headers :as headers]
   [matcher-combinators.test]))

(deftest headers-conversion-test
  (testing "It should convert to and from OkHttp Headers"
    (let [headers {:content-type "application/json"
                   "Authorization" "Bearer dev"}
          result (-> headers
                     headers/map->Headers
                     headers/Headers->map)]

      (is (= result {"content-type" "application/json"
                     "Authorization" "Bearer dev"})))))
