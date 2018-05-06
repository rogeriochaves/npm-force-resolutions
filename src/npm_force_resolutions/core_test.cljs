(ns npm-force-resolutions.core-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [npm-force-resolutions.core :refer [main node-slurp read-json find-resolutions
                                                patch-all-dependencies remove-from-requires
                                                add-dependencies update-package-lock
                                                fix-existing-dependency]]))

(deftest test-read-file
  (let [package-lock-file (node-slurp "./src/fixtures/package-lock.json")]
    (is (re-find #"package-lock-fixture-before" package-lock-file))))

(deftest test-read-package-lock-json
  (let [package-lock (read-json "./src/fixtures/package-lock.json")]
    (is (= (get package-lock "name") "package-lock-fixture-before"))))

(deftest test-find-resolutions
  (let [resolutions (find-resolutions "./src/fixtures")]
    (is (= resolutions
          {"hoek" "4.2.1"
           "example" "1.0.0"}))))

(deftest test-remove-from-require
  (let [resolutions (find-resolutions "./src/fixtures")
        dependency {"requires" {"hoek" "1.0.0"}}
        updated-dependency (remove-from-requires resolutions dependency)]
    (is (= updated-dependency
          {"requires" {}}))))

(deftest test-remove-requires
  (let [resolutions (find-resolutions "./src/fixtures")
        package-lock (read-json "./src/fixtures/package-lock.json")
        updated-package-lock (patch-all-dependencies resolutions package-lock)]
    (is (= {}
          (-> updated-package-lock
            (get "dependencies")
            (get "boom")
            (get "requires"))))))

(deftest test-remove-requires-recursivelly
  (let [resolutions (find-resolutions "./src/fixtures")
        package-lock (read-json "./src/fixtures/package-lock.json")
        updated-package-lock (patch-all-dependencies resolutions package-lock)]
    (is (= {}
          (-> updated-package-lock
            (get "dependencies")
            (get "fsevents")
            (get "dependencies")
            (get "boom")
            (get "requires"))))))

(deftest test-add-dependencies-if-there-is-require
  (let [resolutions (find-resolutions "./src/fixtures")
        dependency {"requires" {"hoek" "1.0.0"}
                    "dependencies" {"foo" {"version" "2.0.0"}}}
        updated-dependency (add-dependencies resolutions dependency)]
    (is (= updated-dependency
          {"requires" {"hoek" "1.0.0"}
           "dependencies" {"foo" {"version" "2.0.0"}
                           "hoek" {"version" "4.2.1"}}}))))

(deftest test-add-dependencies-if-there-is-require-and-no-dependencies
  (let [resolutions (find-resolutions "./src/fixtures")
        dependency {"requires" {"hoek" "1.0.0"}}
        updated-dependency (add-dependencies resolutions dependency)]
    (is (= updated-dependency
          {"requires" {"hoek" "1.0.0"}
           "dependencies" {"hoek" {"version" "4.2.1"}}}))))

(deftest test-do-not-add-dependencies-if-there-is-no-require
  (let [resolutions (find-resolutions "./src/fixtures")
        dependency {"requires" {}
                    "dependencies" {"foo" {"version" "2.0.0"}}}
        updated-dependency (add-dependencies resolutions dependency)]
    (is (= updated-dependency
          {"requires" {}
           "dependencies" {"foo" {"version" "2.0.0"}}}))))

(deftest test-add-dependencies-recursivelly
  (let [resolutions (find-resolutions "./src/fixtures")
        package-lock (read-json "./src/fixtures/package-lock.json")
        updated-package-lock (patch-all-dependencies resolutions package-lock)]
    (is (= {"hoek" {"version" "4.2.1"}}
          (-> updated-package-lock
            (get "dependencies")
            (get "fsevents")
            (get "dependencies")
            (get "boom")
            (get "dependencies"))))))

(deftest test-fix-existing-dependency
  (let [resolutions (find-resolutions "./src/fixtures")
        dependency {"version" "2.16.3"
                    "resolved" "https://registry.npmjs.org/hoek/-/hoek-2.16.3.tgz"
                    "integrity" "sha1-ILt0A9POo5jpHcRxCo/xuCdKJe0="
                    "dev" true}
        updated-dependency (fix-existing-dependency resolutions "hoek" dependency)]
    (is (= updated-dependency
          {"version" "4.2.1"
           "dev" true}))))

(deftest test-does-not-fix-existing-dependency-that-is-not-on-resolutions
  (let [resolutions (find-resolutions "./src/fixtures")
        dependency {"version" "2.16.3"
                    "resolved" "https://registry.npmjs.org/hoek/-/hoek-2.16.3.tgz"
                    "integrity" "sha1-ILt0A9POo5jpHcRxCo/xuCdKJe0="
                    "dev" true}
        updated-dependency (fix-existing-dependency resolutions "foo" dependency)]
    (is (= updated-dependency
          dependency))))

(deftest test-update-package-lock
  (let [expected-package-lock (read-json "./src/fixtures/package-lock.after.json")
        updated-package-lock (update-package-lock "./src/fixtures")]
    (is (= (get expected-package-lock "dependencies")
           (get updated-package-lock "dependencies")))))

(enable-console-print!)
(run-tests)