(ns michelangelo.core-test
  (:require [clojure.test :refer [testing is deftest]]
            [clojure.java.io :as io]
            [quoll.raphael.core :as raphael]
            [donatello.ttl :as ttl]
            [michelangelo.core :refer :all])
  (:import [java.net URI URL]
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
      (is (= output2 "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n@prefix x: <http://x.com#> .\n@prefix y: <http://y.org#> .\n\n"))
      (is (= output3 "x:_1 a x:Example;\n     rdf:value \"ex\".\n\n_:b0 y:p1 1;\n     y:p2 2.\n\n<http://local.com/test/data> x:prop _:b0.\n\n")))))


(def test-output1 "@base <http://example.org/> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n\n<#green-goblin> rel:enemyOf <#spiderman>;\n                a foaf:Person;\n                foaf:name \"Green Goblin\".\n\n<#spiderman> rel:enemyOf <#green-goblin>;\n             a foaf:Person;\n             foaf:name \"Spiderman\", \"Человек-паук\"@ru.\n\n")
(def test-output2 "@base <http://example.org/> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n\n<#green-goblin> rel:enemyOf <#spiderman>;\n                a foaf:Person;\n                foaf:name \"Green Goblin\".\n\n<#spiderman> rel:enemyOf <#green-goblin>;\n             a foaf:Person;\n             foaf:name \"Spiderman\", \"Человек-паук\"@ru.\n\n")
(def test-output3 "@base <http://example.org/> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n@prefix another: <http://more.com/another/> .\n\n<#green-goblin> rel:enemyOf <#spiderman>;\n                a foaf:Person;\n                foaf:name \"Green Goblin\".\n\n<#spiderman> rel:enemyOf <#green-goblin>;\n             a foaf:Person;\n             foaf:name \"Spiderman\", \"Человек-паук\"@ru.\n\n")
(def test-output6 "@base <http://example.org/> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n\n<#green-goblin> rel:enemyOf <#spiderman>;\n                a foaf:Person;\n                foaf:name \"Green Goblin\";\n                foaf:givenname \"Otto\".\n\n<#spiderman> rel:enemyOf <#green-goblin>;\n             a foaf:Person;\n             foaf:name \"Spiderman\", \"Человек-паук\"@ru.\n\n")

(defn file-test
  [tx-fn output id]
  (let [fname (str "test" id ".ttl")]
    (try
      (transform-file "resources/sample.ttl" tx-fn fname)
      (is (= output (slurp fname)))
      (finally 
        (.delete (io/file fname))))))

(deftest test-transform-file
  (testing "checking if file transform works"
    (testing "identity transform"
      (binding [ttl/*include-defaults* false]
        (file-test (fn [g _ _] g) test-output1 1)))
    (testing "identity transform with default namespaces"
      (file-test (fn [g _ _] g) test-output2 2))
    (testing "adding a prefix"
      (file-test (fn [g n b]
                   (let [{:keys [namespaces base]} (meta g)]
                     (is (= namespaces n))
                     (is (= base b)))
                   (with-meta g {:namespaces (assoc n :another "http://more.com/another/") :base b}))
                 test-output3 3))
    (testing "adding a prefix with prefixes as a returned value"
      (file-test (fn [g n b]
                   [g (assoc n :another "http://more.com/another/")])
                 test-output3 4))
    (testing "adding a prefix with prefixes and base as returned values"
      (file-test (fn [g n b]
                   [g (assoc n :another "http://more.com/another/") b])
                 test-output3 5))
    (testing "Adding a new property for first element"
      (file-test (fn [g n b]
                   (assoc-in g [(URI. "http://example.org/#green-goblin") :foaf/givenname] "Otto"))
                 test-output6 6))))
