(ns k16.ok-http
  (:require
   [k16.ok-http.request :as ok-http.request]
   [k16.ok-http.response :as ok-http.response])
  (:import
   [okhttp3 OkHttpClient OkHttpClient$Builder]))

(set! *warn-on-reflection* true)

(defn create-http-client ^OkHttpClient [props]
  (let [client (OkHttpClient$Builder.)]
    (.build client)))

(defn request [^OkHttpClient client request-data]
  (let [request (ok-http.request/map->Request request-data)]
    (-> (.newCall client request)
        .execute
        ok-http.response/Response->map)))
