(ns k16.ok-http.body
  (:require
   [clojure.java.io :as io])
  (:import
   java.io.File
   java.io.InputStream
   [okhttp3 MediaType RequestBody]
   okio.BufferedSink))

(set! *warn-on-reflection* true)

(defn data->RequestBody ^RequestBody [content-type body]
  (when body
    (let [media (MediaType/parse (or content-type "application/octet-stream"))]
      (cond
        (instance? RequestBody body)
        body

        (instance? InputStream body)
        (proxy [RequestBody] []
          (contentLength [] -1)
          (contentType [] media)
          (writeTo [^BufferedSink sink]
            (with-open [in ^InputStream body]
              (io/copy in (.outputStream sink))))
          (isOneShot [] true))

        (string? body)
        (RequestBody/create ^String body media)

        (bytes? body)
        (RequestBody/create ^bytes body media)

        (instance? File body)
        (RequestBody/create ^File body media)

        :else nil))))
