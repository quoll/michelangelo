(ns michelangelo.core-test
  (:require [clojure.test :refer [testing is deftest]]
            [quoll.raphael.core :as raphael]
            [donatello.ttl :as ttl]
            [michelangelo.core :refer :all])
  (:import [java.net URI]
           [java.io StringWriter]))

(def u1 (URI. "http://a.c/subj"))
(def t1 [[:a1 :p1 1] [:a1 :p1 2] [:a1 :p2 "fred"] [u1 :p1 11] [:x/y :p3 "x"]])

(deftest test-graph
  (testing "Creation of a simple graph"
    (let [g1 (simple-graph t1)]
      (is (= g1 {:a1 {:p1 #{1 2}
                      :p2 "fred"}
                 u1 {:p1 11}
                 :x/y {:p3 "x"}})))))

(def tst-graph
  "@base <http://local.com/test/> .
  @prefix x: <http://x.com#> .
  @prefix y: <http://y.org#> .

  x:_1 a x:Example;
       rdf:value \"ex\".
  <data> x:prop [y:p1 1; y:p2 2].")

(def b1 (ttl/->BlankNode 0))

(deftest test-parse
  (testing "Parsing graph into Donatello structures"
    (let [tpls (parse tst-graph)]
      (is (= tpls
             {:x/_1 {:a :x/Example
                     :rdf/value "ex"}
              (URI. "http://local.com/test/data") {:x/prop b1}
              b1 {:y/p1 1
                  :y/p2 2}}))
      (is (= (meta tpls)
             {:base "http://local.com/test/"
              :namespaces {"x" "http://x.com#"
                           "y" "http://y.org#"}})))))

(defmacro as-string
  [expr]
  (let [[op & args] expr]
    `(let [sw# (StringWriter.)]
       (~op sw# ~@args)
       (str sw#))))

(deftest test-write-parse
  (testing "Checking if Donatello can write parsed data"
    (let [p (parse tst-graph)
          m (meta p)
          output1 (as-string (ttl/write-base! (:base m)))
          output2 (as-string (ttl/write-prefixes! (:namespaces m)))
          output3 (as-string (ttl/write-triples-map! p))]
      (is (= output1 "@base <http://local.com/test/> .\n"))
      (is (= output2 "@prefix x: <http://x.com#> .\n@prefix y: <http://y.org#> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n"))
      (is (= output3 "x:_1 a x:Example;\n     rdf:value \"ex\".\n\n_:b0 y:p1 1;\n     y:p2 2.\n\n<http://local.com/test/data> x:prop _:b0.\n\n")))))

