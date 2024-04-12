(ns k16.ok-http
  (:require
   [k16.ok-http.request :as ok-http.request]
   [k16.ok-http.response :as ok-http.response])
  (:import
   java.time.Duration
   java.util.concurrent.TimeUnit
   [okhttp3 ConnectionPool OkHttpClient OkHttpClient$Builder]))

(set! *warn-on-reflection* true)

(defn- ->duration ^Duration [x]
  (Duration/ofMillis x))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn create-connection-pool
  ^ConnectionPool
  [{:keys [max-idle-connections keep-alive-duration-seconds]}]
  (let [max-idle-connections (or max-idle-connections 5)
        keep-alive-duration-seconds (or keep-alive-duration-seconds (* 60 5))]
    (ConnectionPool. max-idle-connections keep-alive-duration-seconds TimeUnit/SECONDS)))

(defn instance-of?
  "Create a schema representing a given Java class"
  ([class-name]
   (instance-of? class-name (str "Should be an instance of " class-name)))
  ([class-name message]
   [:fn {:error/message message} (partial instance? class-name)]))

(def ?OkHttpClient
  (instance-of? OkHttpClient))

(def ?ConnectionPool
  (instance-of? ConnectionPool))

(def ?CreateClientProps
  [:map
   [:connection-pool ?ConnectionPool]

   [:retry-on-connection-failure :boolean]
   [:follow-redirects :boolean]
   [:follow-ssl-redirects :boolean]

   [:call-timeout-ms nat-int?]
   [:connect-timeout-ms nat-int?]
   [:read-timeout-ms nat-int?]
   [:write-timeout-ms nat-int?]
   [:ping-interval-ms nat-int?]])

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn create-client
  {:malli/schema [:=> [:cat ?CreateClientProps] ?OkHttpClient]}
  ^OkHttpClient
  [{:keys [connection-pool
           follow-redirects follow-ssl-redirects

           call-timeout-ms connect-timeout-ms
           read-timeout-ms write-timeout-ms

           ping-interval-ms]}]

  (let [client-builder (OkHttpClient$Builder.)]

    (when (some? connection-pool)
      (.connectionPool client-builder connection-pool))

    (when (some? follow-redirects)
      (.followRedirects client-builder follow-redirects))
    (when (some? follow-ssl-redirects)
      (.followSslRedirects client-builder follow-ssl-redirects))

    (when (some? call-timeout-ms)
      (.callTimeout client-builder (->duration call-timeout-ms)))
    (when (some? connect-timeout-ms)
      (.connectTimeout client-builder (->duration connect-timeout-ms)))
    (when (some? read-timeout-ms)
      (.readTimeout client-builder (->duration read-timeout-ms)))
    (when (some? write-timeout-ms)
      (.writeTimeout client-builder (->duration write-timeout-ms)))
    (when (some? ping-interval-ms)
      (.pingInterval client-builder (->duration ping-interval-ms)))

    (.build client-builder)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn request [^OkHttpClient client request-data]
  (let [request (ok-http.request/map->Request request-data)]
    (-> (.newCall client request)
        .execute
        ok-http.response/Response->map)))
