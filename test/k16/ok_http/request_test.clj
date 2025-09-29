(ns k16.ok-http.request-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [k16.ok-http.request :as request])
  (:import
   [okhttp3 HttpUrl]))

(deftest ->http-url-test
  (testing "It should parse URL without query params"
    (let [result (request/->http-url "https://example.com/api/users" nil)]
      (is (instance? HttpUrl result))
      (is (= "https://example.com/api/users" (str result)))))

  (testing "It should add query parameters"
    (let [result (request/->http-url "https://example.com/api/users"
                                     {:page 1
                                      :limit 10})]
      (is (instance? HttpUrl result))
      (is (= "https://example.com/api/users?page=1&limit=10" (str result)))))

  (testing "It should handle keyword and string keys"
    (let [result (request/->http-url "https://example.com/search"
                                     {:q "clojure"
                                      "sort" "desc"})]
      (is (= "https://example.com/search?q=clojure&sort=desc" (str result)))))

  (testing "It should handle special characters in query params"
    (let [result (request/->http-url "https://example.com/search"
                                     {:q "hello world"
                                      :filter "name=test"})]
      (is (= "https://example.com/search?q=hello%20world&filter=name%3Dtest" (str result)))))

  (testing "It should preserve existing query params in URL"
    (let [result (request/->http-url "https://example.com/api?existing=value"
                                     {:new "param"})]
      (is (= "https://example.com/api?existing=value&new=param" (str result))))))
