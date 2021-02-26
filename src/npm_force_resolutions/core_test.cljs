(ns npm-force-resolutions.core-test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.test :refer-macros [async deftest is testing run-tests]]
            [cljs.core.async :refer [<! >!]]
            [cljs-http.client :as http]
            [xmlhttprequest :refer [XMLHttpRequest]]
            [npm-force-resolutions.core :refer [main node-slurp read-json find-resolutions
                                                patch-all-dependencies update-on-requires
                                                add-dependencies update-package-lock
                                                fix-existing-dependency fetch-resolved-resolution
                                                get-registry-url build-dependency-from-dist
                                                remove-node-modules-path]]))

(set! js/XMLHttpRequest XMLHttpRequest)

(deftest test-read-file
  (let [package-lock-file (node-slurp "./src/fixtures/boom_hoek/package-lock.json")]
    (is (re-find #"package-lock-fixture-before" package-lock-file))))

(deftest test-read-package-lock-json
  (let [package-lock (read-json "./src/fixtures/boom_hoek/package-lock.json")]
    (is (= (get package-lock "name") "package-lock-fixture-before"))))

(deftest test-get-registry-url
  (let [registry-url (get-registry-url)]
    (is (= registry-url "https://registry.npmjs.org/"))))

(deftest test-fetch-resolved-resolution
  (async done
    (go
      (let [resolution (<! (fetch-resolved-resolution "https://registry.npmjs.org/" "hoek" "4.2.1"))]
        (is (= resolution
              {"hoek"
                {"integrity" "sha512-QLg82fGkfnJ/4iy1xZ81/9SIJiq1NGFUMGs6ParyjBZr6jW2Ufj/snDqTHixNlHdPNwN2RLVD0Pi3igeK9+JfA=="
                 "version" "4.2.1"
                 "resolved" "https://registry.npmjs.org/hoek/-/hoek-4.2.1.tgz"}}))
        (done)))))

(deftest test-build-correct-integrity-when-sha512-is-not-available
  (let [dist {:tarball "https://artifactory.xpto.com:443/artifactory/api/npm/npm/axios/-/axios-0.21.1.tgz"
              :shasum "22563481962f4d6bde9a76d516ef0e5d3c09b2b8"}
        dependency (build-dependency-from-dist "0.21.1" dist)]
    (is (= dependency
           {"version" "0.21.1"
            "resolved" "https://artifactory.xpto.com:443/artifactory/api/npm/npm/axios/-/axios-0.21.1.tgz"
            "integrity" "sha1-IlY0gZYvTWvemnbVFu8OXTwJsrg="}))))

(deftest test-skips-integrity-when-no-info-is-available
  (let [dist {:random "thing"}
        dependency (build-dependency-from-dist "^0.21.1" dist)]
    (is (= dependency
           {"version" "^0.21.1"}))))

(deftest test-fetch-resolved-resolution-unfixed-version
  (async done
         (go
          (let [resolution (<! (fetch-resolved-resolution "https://registry.npmjs.org/" "hoek" "^4.2.1"))]
            (is (= resolution
                   {"hoek"
                    {"version" "^4.2.1"}}))
            (done)))))

(def hoek-resolution
  {"integrity" "sha512-QLg82fGkfnJ/4iy1xZ81/9SIJiq1NGFUMGs6ParyjBZr6jW2Ufj/snDqTHixNlHdPNwN2RLVD0Pi3igeK9+JfA=="
    "version" "4.2.1"
    "resolved" "https://registry.npmjs.org/hoek/-/hoek-4.2.1.tgz"})

(def boom-hoek-resolutions
  {"hoek" hoek-resolution
   "webpack"
    {"integrity" "sha512-RC6dwDuRxiU75F8XC4H08NtzUrMfufw5LDnO8dTtaKU2+fszEdySCgZhNwSBBn516iNaJbQI7T7OPHIgCwcJmg=="
      "version" "5.23.0"
      "resolved" "https://registry.npmjs.org/webpack/-/webpack-5.23.0.tgz"}})

(deftest test-find-resolutions
  (async done
    (go
      (let [resolutions (<! (find-resolutions "./src/fixtures/boom_hoek"))]
        (is (= resolutions boom-hoek-resolutions))
        (done)))))

(deftest test-find-resolutions-unfixed
  (async done
         (go
          (let [resolutions (<! (find-resolutions "./src/fixtures/boom_hoek_up"))]
            (is (= resolutions {"hoek" {"version" "\\^4.2.1"}
                                "axios" {"version" "\\~0.19.2"}}))
            (done)))))

(deftest test-updates-from-requires
  (let [dependency {"requires" {"hoek" "1.0.0"}}
        updated-dependency (update-on-requires boom-hoek-resolutions dependency)]
    (is (= updated-dependency
          {"requires" {"hoek" "4.2.1"}}))))

(deftest test-updates-requires
  (let [package-lock (read-json "./src/fixtures/boom_hoek/package-lock.json")
        updated-package-lock (patch-all-dependencies boom-hoek-resolutions package-lock)]
    (is (= {"hoek" "4.2.1"}
          (-> updated-package-lock
            (get "dependencies")
            (get "boom")
            (get "requires"))))))

(deftest test-updates-requires-recursivelly
  (let [package-lock (read-json "./src/fixtures/boom_hoek/package-lock.json")
        updated-package-lock (patch-all-dependencies boom-hoek-resolutions package-lock)]
    (is (= {"hoek" "4.2.1"}
          (-> updated-package-lock
            (get "dependencies")
            (get "fsevents")
            (get "dependencies")
            (get "boom")
            (get "requires"))))))

(deftest test-add-dependencies-if-there-is-require
  (let [dependency {"requires" {"hoek" "1.0.0"}
                    "dependencies" {"foo" {"version" "2.0.0"}}}
        updated-dependency (add-dependencies boom-hoek-resolutions dependency)]
    (is (= updated-dependency
          {"requires" {"hoek" "1.0.0"}
           "dependencies" {"foo" {"version" "2.0.0"}
                           "hoek" hoek-resolution}}))))

(deftest test-add-dependencies-if-there-is-require-and-no-dependencies
  (let [dependency {"requires" {"hoek" "1.0.0"}}
        updated-dependency (add-dependencies boom-hoek-resolutions dependency)]
    (is (= updated-dependency
          {"requires" {"hoek" "1.0.0"}
           "dependencies" {"hoek" hoek-resolution}}))))

(deftest test-do-not-add-dependencies-if-there-is-no-require
  (let [dependency {"requires" {}
                    "dependencies" {"foo" {"version" "2.0.0"}}}
        updated-dependency (add-dependencies boom-hoek-resolutions dependency)]
    (is (= updated-dependency
          {"requires" {}
           "dependencies" {"foo" {"version" "2.0.0"}}}))))

(deftest test-add-dependencies-recursivelly
  (let [package-lock (read-json "./src/fixtures/boom_hoek/package-lock.json")
        updated-package-lock (patch-all-dependencies boom-hoek-resolutions package-lock)]
    (is (= {"hoek" hoek-resolution}
          (-> updated-package-lock
            (get "dependencies")
            (get "fsevents")
            (get "dependencies")
            (get "boom")
            (get "dependencies"))))))

(deftest test-fix-existing-dependency
  (let [dependency {"version" "2.16.3"
                    "resolved" "https://registry.npmjs.org/hoek/-/hoek-2.16.3.tgz"
                    "integrity" "sha1-ILt0A9POo5jpHcRxCo/xuCdKJe0="
                    "dev" true}
        updated-dependency (fix-existing-dependency boom-hoek-resolutions "hoek" dependency)]
    (is (= updated-dependency
          (merge hoek-resolution {"dev" true})))))

(deftest test-does-not-fix-existing-dependency-that-is-not-on-resolutions
  (let [dependency {"version" "2.16.3"
                    "resolved" "https://registry.npmjs.org/hoek/-/hoek-2.16.3.tgz"
                    "integrity" "sha1-ILt0A9POo5jpHcRxCo/xuCdKJe0="
                    "dev" true}
        updated-dependency (fix-existing-dependency boom-hoek-resolutions "foo" dependency)]
    (is (= updated-dependency
          dependency))))

(deftest test-update-package-lock
  (async done
    (go
      (let [expected-package-lock (read-json "./src/fixtures/boom_hoek/package-lock.after.json")
            updated-package-lock (<! (update-package-lock "./src/fixtures/boom_hoek"))]
        (is (= (get expected-package-lock "dependencies")
              (get updated-package-lock "dependencies")))
        (done)))))

(deftest test-update-package-lock-with-require
  (async done
    (go
      (let [updated-package-lock (<! (update-package-lock "./src/fixtures/sfdx-cli_axios"))]
        (is (=
              {"version" "0.19.2"
              "resolved" "https://registry.npmjs.org/axios/-/axios-0.19.2.tgz"
              "integrity" "sha512-fjgm5MvRHLhx+osE2xoekY70AhARk3a6hkN+3Io1jc00jtquGvxYlKlsFUhmUET0V5te6CcZI7lcv2Ym61mjHA=="
              "requires" {
                "follow-redirects" "\\^1.10.0"
              }}
              (-> updated-package-lock
                (get "dependencies")
                (get "@salesforce/telemetry")
                (get "dependencies")
                (get "axios"))))
        (done)))))

(deftest test-update-package-lock-when-version-is-not-fixed
  (async done
         (go
          (let [expected-package-lock (read-json "./src/fixtures/boom_hoek_up/package-lock.after.json")
                updated-package-lock (<! (update-package-lock "./src/fixtures/boom_hoek_up"))]
            (is (= (get expected-package-lock "dependencies")
                   (get updated-package-lock "dependencies")))
            (done)))))

(deftest test-remove-node-modules-path
  (is (= (remove-node-modules-path "node_modules/@oclif/color/node_modules/chalk/node_modules/supports-color")
         "supports-color")))

(deftest test-update-package-lock-packages-on-npm7
  (async done
         (go
          (let [expected-package-lock (read-json "./src/fixtures/npm7/package-lock.after.json")
                updated-package-lock (<! (update-package-lock "./src/fixtures/npm7"))]
            (is (= (get expected-package-lock "dependencies")
                   (get updated-package-lock "dependencies")))
            (is (= (get expected-package-lock "packages")
                   (get updated-package-lock "packages")))
            (done)))))

(enable-console-print!)
(run-tests)