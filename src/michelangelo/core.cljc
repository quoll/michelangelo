(ns ^{:doc "Utilities to connect Raphael and Donatello."
      :author "Paula Gearon"}
  michelangelo.core
  (:require [donatello.ttl :as ttl]
            [quoll.raphael.core :as raphael]
            [tiara.data :refer [ordered-map EMPTY_MAP ordered-set]]
            [quoll.rdf :as rdf]
            #?(:clj [clojure.java.io :as io]))
  #?(:clj (:import [java.net URI])
     :cljs (:import [goog Uri])))

(def ^:dynamic *no-defaults*
  "A flag to indicate that nil bases or namespaces should result in no base or namespace to be written.
  The default is to maintain the existing base and namespace if none are specified."
  false)

(defn uri [s] #?(:clj (URI. s) :cljs (Uri. s)))

(defrecord RoundTripGenerator [counter bnode-cache namespaces]
  raphael/NodeGenerator
  (new-node [this]
    [(update this :counter inc) (rdf/unsafe-blank-node (str "_:b" counter))])
  (new-node [this label]
    (if-let [node (get bnode-cache label)]
      [this node]
      (let [node (rdf/unsafe-blank-node (str "_:b" counter))]
        [(-> this
             (update :counter inc)
             (update :bnode-cache assoc label node))
         node])))
  (add-base [this iri] (update this :namespaces assoc :base (str iri)))
  (add-prefix [this prefix iri] (update this :namespaces assoc prefix (str iri)))
  (iri-for [this prefix] (get namespaces prefix))
  (get-namespaces [this] (dissoc namespaces :base))
  (get-base [this] (:base namespaces))
  (new-qname [this prefix local] (keyword prefix local))
  (new-iri [this iri] (uri iri))
  (new-literal [this s] s)
  (new-literal [this s t] (rdf/typed-literal s t))
  (new-lang-string [this s lang] (rdf/lang-literal s lang))
  (rdf-type [this] :a)
  (rdf-first [this] :rdf/first)
  (rdf-rest [this] :rdf/rest)
  (rdf-nil [this] :rdf/nil))

(defn round-trip-generator
  "Creates a new RoundTripGenerator"
  []
  (->RoundTripGenerator 0 {} EMPTY_MAP))

(defn index-add
  "Merges a single triple into a nested map"
  [idx [a b c]]
  (if-let [idxb (get idx a)]
    (if-let [idxc (get idxb b)]
      (if (set? idxc)
        (if (get idxc c)
          idx
          (assoc idx a (assoc idxb b (conj idxc c))))
        (assoc idx a (assoc idxb b (ordered-set idxc c))))
      (assoc idx a (assoc idxb b c)))
    (assoc idx a (ordered-map b c))))

(defn add-all
  "Inserts all triples in a sequence into a nested map"
  [idx st]
  (reduce index-add idx st))

(defn simple-graph
  "Creates a nested-map version of a graph from a sequence of triples"
  [triples]
  (add-all (ordered-map) triples))

(defn parsed-graph
  "Converts a graph parsed by Raphel into a nested map, with metadata for the prefixes and base."
  [{:keys [base namespaces triples] :as parsed}]
  (with-meta (simple-graph triples) {:namespaces namespaces :base base}))

(defn parse
  "Parses input and creates a graph"
  [s]
  (parsed-graph (raphael/parse s (round-trip-generator))))

(defn write-graph
  "Writes a parsed graph to a stream as Turtle.
   out - An OutputStream to write to.
   g - The graph to write.
   Tha base and namespace prefixes for the graph may included as:
     - metadata (as a map with keys of `:namespaces` and `:base`).
     - a map (as per the metadata map).
     - as individual arguments."
  ([out g]
   (let [{:keys [namespaces base]} (meta g)]
     (write-graph out g namespaces base)))
  ([out g {:keys [namespaces base]}] (write-graph out g namespaces base))
  ([out g namespaces base]
   (when base (ttl/write-base! out base))
   (when namespaces (ttl/write-prefixes! out namespaces))
   (binding [ttl/*context-base* base]
     (ttl/write-triples-map! out g))))

#?(:clj
   (defn transform-file
     "Transforms a TTL file.
     Accepts an input filename, a transforming function, and an output filename.
     The transforming function receives:
     - graph: the graph to transform.
     - namespaces: The prefix namespaces of of the graph.
     - base: The base of the graph.
     The result may be one of:
     - a vector of: [new-graph namespaces base]
     - a graph with a meta map of `:namespaces` and `:base`
     Both the base and namespaces are optional to return. If they are not returned, then the
     previous base and namespaces will be used, unless *no-default* has been set."
     [infile tx-fn outfile]
     (let [graph (parse (slurp infile))
           {:keys [namespaces base] :as context} (meta graph)
           tx-result (tx-fn graph namespaces base)
           [new-graph ret-namespaces ret-base] (if (vector? tx-result)
                                                 tx-result
                                                 (let [{:keys [namespaces base]} (meta tx-result)]
                                                   [tx-result namespaces base]))
           new-namespaces (if *no-defaults* ret-namespaces (or ret-namespaces namespaces))
           new-base (if *no-defaults* ret-base (or ret-base base))]
       (with-open [out (io/writer outfile)]
         (write-graph out new-graph new-namespaces new-base)))))


(defn transform-string
  "Transforms a string containing TTL.
  Accepts an input string, and a transforming function. Returns a string containing the transformed TTL.
  The transforming function receives:
  - graph: the graph to transform.
  - namespaces: The prefix namespaces of of the graph.
  - base: The base of the graph.
  The result may be one of:
  - a vector of: [new-graph namespaces base]
  - a graph with a meta map of `:namespaces` and `:base`
  Both the base and namespaces are optional to return. If they are not returned, then the
  previous base and namespaces will be used, unless *no-default* has been set."
  [in tx-fn]
  (let [graph (parse in)
        {:keys [namespaces base] :as context} (meta graph)
        tx-result (tx-fn graph namespaces base)
        [new-graph ret-namespaces ret-base] (if (vector? tx-result)
                                              tx-result
                                              (let [{:keys [namespaces base]} (meta tx-result)]
                                                [tx-result namespaces base]))
        new-namespaces (if *no-defaults* ret-namespaces (or ret-namespaces namespaces))
        new-base (if *no-defaults* ret-base (or ret-base base))]
    (ttl/to-string write-graph new-graph new-namespaces new-base)))
