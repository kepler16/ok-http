(ns k16.ok-http.request
  (:require
   [clojure.string :as str]
   [k16.ok-http.body :as ok-http.body]
   [k16.ok-http.headers :as ok-http.headers])
  (:import
   [okhttp3 Request Request$Builder RequestBody]
   [okhttp3.internal.http HttpMethod]))

(set! *warn-on-reflection* true)

(defn map->Request ^Request [{:keys [request-method body headers url]}]
  (let [method (str/upper-case (name request-method))
        headers (ok-http.headers/map->Headers headers)
        content-type (.get headers "content-type")
        body (ok-http.body/data->RequestBody content-type body)

        body (if (HttpMethod/requiresRequestBody method)
               (or body (RequestBody/create (byte-array 0) nil))
               body)

        request (Request$Builder.)]

    (doto request
      (.method method body)
      (.headers headers)
      (.url ^String url))

    (.build request)))
