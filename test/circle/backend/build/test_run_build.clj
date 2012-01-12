(ns circle.backend.build.test-run-build
  (:use midje.sweet)
  (:use [circle.backend.build.test-utils :only (minimal-build ensure-test-user-and-project ensure-test-project test-build-id ensure-test-build)])
  (:use [circle.backend.action :only (defaction action)])
  (:use [circle.backend.build :only (build successful?)])
  (:use [circle.backend.build.run :only (run-build)])
  (:use [circle.backend.build.config :only (build-from-url)])
  (:require circle.system)
  (:require [somnium.congomongo :as mongo])
  (:use [circle.util.predicates :only (ref?)]))

(circle.db/init)
(ensure-test-user-and-project)
(ensure-test-build)

(defaction successful-action [act-name]
  {:name act-name}
  (fn [build]
    nil))

(defn successful-build []
  (minimal-build :actions [(successful-action "1")
                           (successful-action "2")
                           (successful-action "3")]))

(fact "successful build is successful"
  (let [build (run-build (successful-build))]
    build => ref?
    @build => map?
    (-> @build :action-results) => seq
    (-> @build :action-results (count)) => 3
    (for [res (-> @build :action-results)]
      (> (-> res :stop-time) (-> res :start-time)) => true)
    (successful? build) => truthy))

(fact "dummy project does not start nodes"
  ;;; This should be using the empty template, which does not start nodes
  (-> "https://github.com/arohner/circle-dummy-project"
      (build-from-url)
      deref
      :actions
      (count)) => 0)

(fact "build of dummy project is successful"
  (-> "https://github.com/arohner/circle-dummy-project" (build-from-url) (run-build) (successful?)) => true)

(fact "builds insert into the DB"
  (let [build (run-build (successful-build))]
    (successful? build) => truthy
    (-> @build :_project_id) => truthy
    (-> @build :build_num) => integer?
    (-> @build :build_num) => pos?
    (let [builds (mongo/fetch :builds :where {:_id (-> @build :_id)})]
      (count builds) => 1)))

(fact "builds using the provided objectid"
  (let [build (run-build (successful-build) :id test-build-id)
        builds (mongo/fetch :builds :where {:_id test-build-id})]
    (count builds) => 1))

(fact "successive builds use incrementing build-nums"
  (let [first-build (run-build (successful-build))
        second-build (run-build (successful-build))]
    (> (-> @second-build :build_num) (-> @first-build :build_num)) => true))
