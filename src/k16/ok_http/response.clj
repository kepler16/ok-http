(ns k16.ok-http.response
  (:require
   [k16.ok-http.headers :as ok-http.headers])
  (:import
   okhttp3.Response))

(defn Response->map [^Response response]
  {:status (.code response)
   :headers (ok-http.headers/Headers->map (.headers response))
   :body (-> response .body .byteStream)})
