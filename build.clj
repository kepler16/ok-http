(ns build
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deps-deploy]))

(def basis (delay (b/create-basis {})))

(def lib 'com.kepler16/ok-http)
(def version (str/replace (or (System/getenv "VERSION") "0.0.0") #"v" ""))
(def class-dir "target/classes")
(def jar-file "target/lib.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :pom-data [[:description "Clojure wrapper for https://github.com/square/okhttp"]
                           [:url "https://github.com/kepler16/ok-http"]
                           [:licenses
                            [:license
                             [:name "MIT"]
                             [:url "https://opensource.org/license/mit"]]]]})

  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})

  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn release [_]
  (deps-deploy/deploy {:installer :remote
                       :artifact (b/resolve-path jar-file)
                       :pom-file (b/pom-path {:lib lib
                                              :class-dir class-dir})}))
