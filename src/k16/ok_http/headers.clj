(ns k16.ok-http.headers
  (:import
   [okhttp3 Headers Headers$Builder]))

(set! *warn-on-reflection* true)

(defn map->Headers ^Headers [headers]
  (let [builder (Headers$Builder.)]
    (doseq [[k v] headers]
      (.add builder (name k) ^String (if (keyword? v) (name v) (str v))))
    (.build builder)))

(defn Headers->map [^Headers headers]
  (->> (range (.size headers))
       (reduce
        (fn [acc i]
          (let [key (.name headers i)
                value (.value headers i)]
            (assoc! acc key value)))
        (transient {}))
       persistent!))
