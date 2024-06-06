(ns k16.ok-http
  (:require
   [k16.ok-http.request :as ok-http.request]
   [k16.ok-http.response :as ok-http.response])
  (:import
   [java.io IOException]
   java.time.Duration
   java.util.concurrent.Executors
   java.util.concurrent.TimeUnit
   [okhttp3
    Call
    Callback
    ConnectionPool
    Dispatcher
    OkHttpClient
    OkHttpClient$Builder
    Response]))

(set! *warn-on-reflection* true)

(defn- ->duration ^Duration [x]
  (Duration/ofMillis x))

(def ?OkHttpClient
  [:fn {:error/message "Must be instance of OkHttpClient"}
   (fn [v] (instance? OkHttpClient v))])

(def timeout-opts
  [[:call-timeout-ms {:optional true} nat-int?]
   [:connect-timeout-ms {:optional true} nat-int?]
   [:read-timeout-ms {:optional true} nat-int?]
   [:write-timeout-ms {:optional true} nat-int?]])

(def ?RequestData
  [:map
   [:request-method {:optional true} :keyword]
   [:body {:optional true} :any]
   [:headers {:optional true} :map]
   [:timeout-opts {:optional true}
    (into [:map] timeout-opts)]
   [:url :string]])

(def ?Callbacks
  [:map
   [:on-failure {:optional true} fn?]
   [:on-response {:optional true} fn?]])

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn create-dispatcher
  ^Dispatcher
  [{:keys [executor-service idle-callback
           max-requests max-requests-per-host]}]
  (let [dispatcher (if executor-service
                     (Dispatcher. executor-service)
                     (Dispatcher.))]
    (some->> idle-callback (.setIdleCallback dispatcher))
    (some->> max-requests (.setMaxRequests dispatcher))
    (some->> max-requests-per-host (.setMaxRequestsPerHost dispatcher))
    dispatcher))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn create-connection-pool
  ^ConnectionPool
  [{:keys [max-idle-connections keep-alive-duration-seconds]}]
  (let [max-idle-connections (or max-idle-connections 5)
        keep-alive-duration-seconds (or keep-alive-duration-seconds (* 60 5))]
    (ConnectionPool. max-idle-connections keep-alive-duration-seconds TimeUnit/SECONDS)))

(defonce ^{:private true
           :doc "Default virtual threads dispatcher"}
  virtual-threads-dispatcher
  (try
    (-> Executors
        (.getMethod "newVirtualThreadPerTaskExecutor" (into-array Class []))
        (.invoke nil (into-array []))
        (Dispatcher.))
    (catch Throwable _
      nil)))

(def ?CreateClientProps
  (into
   [:map
    [:connection-pool {:optional true} :any]
    [:dispatcher {:optional true} :any]
    [:protocols {:optional true} [:sequential :any]]

    [:retry-on-connection-failure {:optional true} :boolean]
    [:follow-redirects {:optional true} :boolean]
    [:follow-ssl-redirects {:optional true} :boolean]

    [:ping-interval-ms {:optional true} nat-int?]]
   timeout-opts))

(defn- set-options!
  ^OkHttpClient$Builder
  [^OkHttpClient$Builder client-builder
   {:keys [connection-pool dispatcher protocols
           follow-redirects follow-ssl-redirects

           call-timeout-ms connect-timeout-ms
           read-timeout-ms write-timeout-ms

           retry-on-connection-failure
           ping-interval-ms]}]
  (when (some? connection-pool)
    (.connectionPool client-builder connection-pool))

  (when (some? dispatcher)
    (.dispatcher client-builder dispatcher))

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
  (when (some? protocols)
    (.protocols client-builder protocols))
  (when (boolean? retry-on-connection-failure)
    (.retryOnConnectionFailure client-builder retry-on-connection-failure))
  client-builder)

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn create-client
  {:malli/schema [:=> [:cat ?CreateClientProps] :any]}
  ^OkHttpClient
  ([] (create-client {}))
  ([{:keys [dispatcher] :as options}]
   (let [client-builder (OkHttpClient$Builder.)
         dispatcher' (or dispatcher virtual-threads-dispatcher)]
     (set-options! client-builder (assoc options :dispatcher dispatcher'))
     (.build client-builder))))

(defn set-timeouts!
  ^OkHttpClient
  [^OkHttpClient client override-options]
  (if (seq override-options)
    (-> (.newBuilder client)
        (set-options! override-options)
        (.build))
    client))

(defn- ->Callback ^Callback
  [{:keys [on-failure on-response] :as callbacks}]
  (when (seq callbacks)
    (reify Callback
      (^void onFailure [_ ^Call _call ^IOException ex]
        (if on-failure
          (on-failure ex)
          (.printStackTrace ex)))
      (^void onResponse [_ ^Call _call ^Response response]
        (when on-response
          (-> response
              (ok-http.response/Response->map)
              (on-response)))))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn request
  {:doc "Performs a synchronous HTTP request using OkHttpClient.
  
  Parameters:
  - `client`: OkHttpClient instance.
  - `request-data`: Map containing request details, including optional timeout settings.

  `request-data`:
  - `timeout-opts`: Optional map of timeout settings.

  Returns:
  - A map representation of the response.

  Example:
  (request client {:url \"https://api.example.com\"})"
   :malli/schema [:=> [:cat ?OkHttpClient ?RequestData] :any]}
  ([^OkHttpClient client request-data]
   (let [client' (if (:timeout-opts request-data)
                   (set-timeouts! client (:timeout-opts request-data))
                   client)
         request (ok-http.request/map->Request request-data)]
     (-> (.newCall client' request)
         (.execute)
         (ok-http.response/Response->map)))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn request-async
  {:doc "Performs an asynchronous cancellable HTTP request using OkHttpClient.
  
  Parameters:
  - `client`: OkHttpClient instance.
  - `request-data`: Map containing request details, including optional timeout settings.
  - `callbacks`: Map of callback functions for async handling.

  `request-data`:
  - `timeout-opts`: Optional map of timeout settings.

  `callbacks`:
  - `on-response`: Function to be called on successful response. Receives the response as a map.
  - `on-failure`: Function to be called on request failure. Receives the exception as a parameter.

  Returns:
  - A function that cancels the request when called.

  Example:
  (request-async client {:url \"https://api.example.com\"} {:on-response success-fn :on-failure failure-fn})"
   :malli/schema [:=> [:cat ?OkHttpClient ?RequestData ?Callbacks] :any]}
  [^OkHttpClient client {:keys [timeout-opts] :as request-data} callbacks]
  (let [client' (if timeout-opts
                  (set-timeouts! client timeout-opts)
                  client)
        request (ok-http.request/map->Request request-data)
        ^Call call (.newCall client' request)
        ok-http-callback (->Callback callbacks)]
    (.enqueue call ok-http-callback)
    (fn [] (.cancel call))))

