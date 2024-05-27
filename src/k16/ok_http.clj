(ns k16.ok-http
  (:require
   [k16.ok-http.request :as ok-http.request]
   [k16.ok-http.response :as ok-http.response])
  (:import
   java.lang.reflect.Method
   java.time.Duration
   java.util.concurrent.Executors
   java.util.concurrent.TimeUnit
   [okhttp3
    ConnectionPool
    Dispatcher
    OkHttpClient
    OkHttpClient$Builder]))

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

(defn- has-method? [klass name]
  (let [methods (into #{}
                      (map (fn [method] (.getName ^Method method)))
                      (.getDeclaredMethods ^Class klass))]
    (contains? methods name)))

;; Taken from funcool/promesa:
;; https://github.com/funcool/promesa/blob/e503874b154224ce85b223144e80b697df91d18e/src/promesa/exec.cljc#L61
(def ^:private virtual-threads-available?
  "Var that indicates the availability of virtual threads."
  (if (and (has-method? Thread "ofVirtual")
           (try (eval '(Thread/ofVirtual))
                (catch Exception _ false)))
    true
    false))

(def ?CreateClientProps
  [:map
   [:connection-pool {:optional true} :any]
   [:dispatcher {:optional true} :any]
   [:protocols {:optional true} [:sequential :any]]

   [:retry-on-connection-failure {:optional true} :boolean]
   [:follow-redirects {:optional true} :boolean]
   [:follow-ssl-redirects {:optional true} :boolean]

   [:call-timeout-ms {:optional true} nat-int?]
   [:connect-timeout-ms {:optional true} nat-int?]
   [:read-timeout-ms {:optional true} nat-int?]
   [:write-timeout-ms {:optional true} nat-int?]
   [:ping-interval-ms {:optional true} nat-int?]])

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn create-client
  {:malli/schema [:=> [:cat ?CreateClientProps] :any]}
  ^OkHttpClient
  ([] (create-client {}))
  ([{:keys [connection-pool dispatcher protocols
            follow-redirects follow-ssl-redirects

            call-timeout-ms connect-timeout-ms
            read-timeout-ms write-timeout-ms

            ping-interval-ms]}]

   (let [client-builder (OkHttpClient$Builder.)
         dispatcher (or dispatcher
                        (when virtual-threads-available?
                          (Dispatcher. (Executors/newVirtualThreadPerTaskExecutor))))]

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

     (.build client-builder))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn request [^OkHttpClient client request-data]
  (let [request (ok-http.request/map->Request request-data)]
    (-> (.newCall client request)
        .execute
        ok-http.response/Response->map)))
