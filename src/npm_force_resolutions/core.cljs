(ns npm-force-resolutions.core
  (:require [cljs.nodejs :as nodejs]
            [cognitect.transit :as t]))

(defn main [& args]
  (str "args: " (first args)))

(defn node-slurp [path]
  (let [fs (nodejs/require "fs")]
    (.readFileSync fs path "utf8")))

(defn read-json [path]
  (t/read (t/reader :json) (node-slurp path)))

(defn find-resolutions [folder]
  (let [package-json (read-json (str folder "/package.json"))]
    (get package-json "resolutions")))

(defn remove-from-requires [resolutions dependency]
  (update dependency "requires" #(apply dissoc % (keys resolutions))))

; Source: https://stackoverflow.com/questions/1676891/mapping-a-function-on-the-values-of-a-map-in-clojure
(defn map-vals [f m]
  (apply merge (map (fn [[k v]] {k (f k v)}) m)))

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

(defn patch-dependency [resolutions key dependency]
  (if (contains? dependency "requires")
    (->> dependency
        (add-dependencies resolutions)
        (remove-from-requires resolutions)
        (fix-existing-dependency resolutions key)
        (patch-all-dependencies resolutions))
    (fix-existing-dependency resolutions key dependency)))

(defn patch-all-dependencies [resolutions package-lock]
  (update package-lock "dependencies"
    (fn [dependencies]
      (map-vals #(patch-dependency resolutions %1 %2) dependencies))))

(defn update-package-lock [folder]
  (let [package-lock (read-json (str folder "/package-lock.json"))
        resolutions (find-resolutions folder)]
    (patch-all-dependencies resolutions package-lock)))

(set! *main-cli-fn* main)