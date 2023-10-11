(ns michelangelo.core-test
  (:require [clojure.test :refer [testing is deftest]]
            #?(:clj [clojure.java.io :as io])
            [quoll.raphael.core :as raphael]
            [donatello.ttl :as ttl]
            [quoll.rdf :as rdf]
            [michelangelo.core :refer [uri simple-graph parse transform-string #?(:clj transform-file)]]
            [michelangelo.test-data :as tdata]))

(def u1 (uri "http://a.c/subj"))
(def t1 [[:a1 :p1 1] [:a1 :p1 2] [:a1 :p2 "fred"] [u1 :p1 11] [:x/y :p3 "x"]])

#?(:cljs
   (extend-protocol IEquiv
     goog.Uri
     (-equiv [a b]
       (and (= goog.Uri (type b)) (= (str a) (str b))))))

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

(def b1 (rdf/unsafe-blank-node "_:b0"))

(deftest test-parse
  (testing "Parsing graph into Donatello structures"
    (let [tpls (parse tst-graph)]
      (is (= tpls
             {:x/_1 {:a :x/Example
                     :rdf/value "ex"}
              (uri "http://local.com/test/data") {:x/prop b1}
              b1 {:y/p1 1
                  :y/p2 2}}))
      (is (= (meta tpls)
             {:base "http://local.com/test/"
              :namespaces {"x" "http://x.com#"
                           "y" "http://y.org#"}})))))
(deftest test-write-parse
  (testing "Checking if Donatello can write parsed data"
    (let [p (parse tst-graph)
          m (meta p)
          output1 (ttl/to-string ttl/write-base! (:base m))
          output2 (ttl/to-string ttl/write-prefixes! (:namespaces m))
          output3 (ttl/to-string ttl/write-triples-map! p)]
      (is (= output1 "@base <http://local.com/test/> .\n"))
      (is (= output2 "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n@prefix x: <http://x.com#> .\n@prefix y: <http://y.org#> .\n\n"))
      (is (= output3 "x:_1 a x:Example;\n     rdf:value \"ex\".\n\n_:b0 y:p1 1;\n     y:p2 2.\n\n<http://local.com/test/data> x:prop _:b0.\n\n")))))


(def test-output1 "@base <http://example.org/> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n\n<#green-goblin> rel:enemyOf <#spiderman>;\n                a foaf:Person;\n                foaf:name \"Green Goblin\".\n\n<#spiderman> rel:enemyOf <#green-goblin>;\n             a foaf:Person;\n             foaf:name \"Spiderman\", \"Человек-паук\"@ru.\n\n")
(def test-output2 "@base <http://example.org/> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n\n<#green-goblin> rel:enemyOf <#spiderman>;\n                a foaf:Person;\n                foaf:name \"Green Goblin\".\n\n<#spiderman> rel:enemyOf <#green-goblin>;\n             a foaf:Person;\n             foaf:name \"Spiderman\", \"Человек-паук\"@ru.\n\n")
(def test-output3 "@base <http://example.org/> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n@prefix another: <http://more.com/another/> .\n\n<#green-goblin> rel:enemyOf <#spiderman>;\n                a foaf:Person;\n                foaf:name \"Green Goblin\".\n\n<#spiderman> rel:enemyOf <#green-goblin>;\n             a foaf:Person;\n             foaf:name \"Spiderman\", \"Человек-паук\"@ru.\n\n")
(def test-output6 "@base <http://example.org/> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n\n<#green-goblin> rel:enemyOf <#spiderman>;\n                a foaf:Person;\n                foaf:name \"Green Goblin\";\n                foaf:givenname \"Otto\".\n\n<#spiderman> rel:enemyOf <#green-goblin>;\n             a foaf:Person;\n             foaf:name \"Spiderman\", \"Человек-паук\"@ru.\n\n")

(defn full-transform-test
  [tx-test]
  (testing "checking if file transform works"
    (testing "identity transform"
      (binding [ttl/*include-defaults* false]
        (tx-test (fn [g _ _] g) test-output1 1)))
    (testing "identity transform with default namespaces"
      (tx-test (fn [g _ _] g) test-output2 2))
    (testing "adding a prefix"
      (tx-test (fn [g n b]
                   (let [{:keys [namespaces base]} (meta g)]
                     (is (= namespaces n))
                     (is (= base b)))
                   (with-meta g {:namespaces (assoc n :another "http://more.com/another/") :base b}))
                 test-output3 3))
    (testing "adding a prefix twice"
      (tx-test (fn [g n b]
                   (let [{:keys [namespaces base]} (meta g)]
                     (is (= namespaces n))
                     (is (= base b)))
                   (with-meta g {:namespaces (-> n
                                                 (assoc "another" "http://more.com/another/")
                                                 (assoc :another "http://more.com/another/extra"))
                                 :base b}))
                 test-output3 3))
    (testing "adding a prefix with prefixes as a returned value"
      (tx-test (fn [g n b]
                   [g (assoc n :another "http://more.com/another/")])
                 test-output3 4))
    (testing "adding a prefix with prefixes and base as returned values"
      (tx-test (fn [g n b]
                   [g (assoc n :another "http://more.com/another/") b])
                 test-output3 5))
    (testing "Adding a new property for first element"
      (tx-test (fn [g n b]
                   (assoc-in g [(uri "http://example.org/#green-goblin") :foaf/givenname] "Otto"))
                 test-output6 6))))

#?(:clj
   (defn file-test
     [tx-fn output id]
     (let [fname (str "test" id ".ttl")]
       (try
         (transform-file "resources/sample.ttl" tx-fn fname)
         (is (= output (slurp fname)))
         (finally 
           (.delete (io/file fname)))))))

#?(:clj
   (deftest test-transform-file
     (testing "checking if file transform works"
       (full-transform-test file-test))))

(defn string-test
  [tx-fn output id]
  (is (= output (transform-string tdata/data tx-fn))))

(deftest test-transform-string
  (testing "checking if string transform works"
    (full-transform-test string-test)))

#?(:cljs (cljs.test/run-tests))
