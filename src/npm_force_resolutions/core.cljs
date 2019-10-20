(ns npm-force-resolutions.core
  (:require [cljs.nodejs :as nodejs]
            [clojure.string :as string]
            [cognitect.transit :as t]))

(defn node-slurp [path]
  (let [fs (nodejs/require "fs")]
    (.readFileSync fs path "utf8")))

(defn node-spit [path data]
  (let [fs (nodejs/require "fs")]
    (.writeFileSync fs path data)))

(defn read-json [path]
  (t/read (t/reader :json) (string/replace (node-slurp path) #"\^" "\\\\^")))

(defn find-resolutions [folder]
  (let [package-json (read-json (str folder "/package.json"))]
    (get package-json "resolutions")))

; Source: https://stackoverflow.com/questions/1676891/mapping-a-function-on-the-values-of-a-map-in-clojure
(defn map-vals [f m]
  (apply merge (map (fn [[k v]] {k (f k v)}) m)))

(defn update-on-requires [resolutions dependency]
  (update dependency "requires"
    (fn [requires]
      (map-vals #((fn [key version]
        (if (contains? resolutions key)
          (get resolutions key)
          version)
      ) %1 %2) requires))))

(defn add-dependencies [resolutions dependency]
  (let [required-dependencies (keys (get dependency "requires"))
        new-dependencies (select-keys
                          (map-vals (fn [k v] {"version" v}) resolutions)
                          required-dependencies)
        with-deps (merge-with into dependency {"dependencies" {}})]
    (update with-deps "dependencies" #(conj % new-dependencies))))

(defn fix-existing-dependency [resolutions key dependency]
  (if (contains? resolutions key)
    (conj (dissoc dependency "version" "resolved" "integrity" "bundled") {"version" (get resolutions key)})
    dependency))

(defn order-map [target]
  (into (sorted-map-by (fn [key1 key2]
                         (compare [key1]
                                  [key2]))) target))

(defn sort-or-remove-map [key dependency]
  (if (map? (get dependency key))
    (update dependency key order-map)
    (dissoc dependency key)))

(declare patch-all-dependencies)

(defn patch-dependency [resolutions key dependency]
  (if (contains? dependency "requires")
    (->> dependency
        (add-dependencies resolutions)
        (update-on-requires resolutions)
        (fix-existing-dependency resolutions key)
        (patch-all-dependencies resolutions)
        (sort-or-remove-map "dependencies")
        (sort-or-remove-map "requires"))
    (fix-existing-dependency resolutions key dependency)))

(defn patch-all-dependencies [resolutions package-lock]
  (update package-lock "dependencies"
    (fn [dependencies]
      (map-vals #(patch-dependency resolutions %1 %2) dependencies))))

(defn update-package-lock [folder]
  (let [package-lock (read-json (str folder "/package-lock.json"))
        resolutions (find-resolutions folder)]
    (->> (patch-all-dependencies resolutions package-lock)
         (sort-or-remove-map "dependencies"))))

(defn indent-json [json]
  (let [json-format (nodejs/require "json-format")]
    (-> (.parse js/JSON json)
        (json-format (js-obj "type" "space"
                             "size" 2))
        (string/replace #"\\\\\^" "^")
        (string/replace #" +\n" ""))))

(defn main [& args]
  (let [folder (or (first args) ".")
        package-lock (update-package-lock folder)
        package-lock-json (t/write (t/writer :json-verbose) package-lock)]
    (node-spit (str folder "/package-lock.json") (indent-json package-lock-json))))

(set! *main-cli-fn* main)