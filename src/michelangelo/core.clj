(ns ^{:doc "Utilities to connect Raphael and Donatello."
      :author "Paula Gearon"}
  michelangelo.core
  (:require [donatello.ttl :as ttl]
            [quoll.raphael.core :as raphael]
            [tiara.data :refer [ordered-map EMPTY_MAP ordered-set]])
  (:import [java.net URI]))


(defrecord RoundTripGenerator [counter bnode-cache namespaces]
  raphael/NodeGenerator
  (new-node [this]
    [(update this :counter inc) (ttl/->BlankNode counter)])
  (new-node [this label]
    (if-let [node (get bnode-cache label)]
      [this node]
      (let [node (ttl/->BlankNode counter)]
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
  (new-iri [this iri] (URI. iri))
  (new-literal [this s] s)
  (new-literal [this s t] (ttl/typed-literal s t))
  (new-lang-string [this s lang] (ttl/lang-literal s lang))
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
  "Writes a parsed graph to a stream as Turtle"
  [out g]
  (let [{:keys [namespaces base]} (meta g)]
    (ttl/write-base! out base)
    (ttl/write-prefixes! out namespaces)
    (ttl/write-triples-map! out g)))

