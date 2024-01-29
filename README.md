# michelangelo
[Turtle](https://www.w3.org/TR/turtle/) transforming library for Clojure and ClojureScript.

## deps.edn
Add the following to the `:deps` map in `deps.edn`:

```clojure
org.clojars.quoll/michelangelo {:mvn/version "0.1.10"}
```

## Usage
This library uses [Raphael](https://github.com/quoll/raphael) to parse [Turtle](https://www.w3.org/TR/turtle/) into an ordered nested map, where it can be modified, appended to, etc. These structures can then be written to new Turtle files using [Donatello](https://github.com/quoll/donatello):

```clojure
(require '[michelangelo.core :as m])
(m/transform-file
  "resources/sample.ttl"
  (fn [graph namespaces base]
    [(assoc graph :ex/subject {:ex/predicate "object 1"
                               :ex/predicate2 #{1 2 3}})
     (assoc namespaces :ex "http://ex.com/")
     base])
  "resources/destination.ttl")
```

This will take the following sample TTL file:
```ttl
@base <http://example.org/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .
@prefix rel: <http://www.perceive.net/schemas/relationship/> .

<#green-goblin> rel:enemyOf <#spiderman> ;
                a foaf:Person ;    # in the context of the Marvel universe
                foaf:name "Green Goblin" .

<#spiderman> rel:enemyOf <#green-goblin> ;
             a foaf:Person ;
             foaf:name "Spiderman", "Человек-паук"@ru .
```
And writes out the following file:
```ttl
@base <http://example.org/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .
@prefix rel: <http://www.perceive.net/schemas/relationship/> .
@prefix ex: <http://ex.com/> .

<#green-goblin> rel:enemyOf <#spiderman>;
                a foaf:Person;
                foaf:name "Green Goblin".

<#spiderman> rel:enemyOf <#green-goblin>;
             a foaf:Person;
             foaf:name "Spiderman", "Человек-паук"@ru.

ex:subject ex:predicate "object 1";
           ex:predicate2 1, 3, 2.
```
A few things to note:
- Comments are not preserved.
- Order is preserved.
- By default, the base and prefixes will all be preserved, even if they are not returned.
- URLs and URIs that match the base will be rewritten as relative IRIs.
- URLs and URIs that match a prefix will be rewritten as a QName.

### Details
If you want to do the transformation in parts, then you can do the following:
 * Parse the document into a graph (the context is returned as metadata).
 * Update the graph and/or the context.
 * Write the context and graph to an output stream.

This is demonstrated here:

```clojure
(require '[michelangelo.core :as m])
(require '[clojure.java.io :as io])

(let [graph (m/parse (slurp "resources/sample.ttl"))
      context (meta graph)]

  ;; context is a map of :namespaces and :base
  ;; update to include a new prefix
  ;; add new triples that uses this prefix
  (let [new-context (update context :namespaces assoc :ex "http://ex.com/")
        new-graph (assoc graph :ex/subject {:ex/predicate "object 1"
                                            :ex/predicate2 #{1 2 3}})]

    ;; The context should be added back as meta, just as it arrived
    (with-open [out (io/writer "resources/destination.ttl")]
      (m/write-graph out (with-meta new-graph new-context)))))
```
The `transform-file` operation essentially does this, with a couple of tweaks.

### Context as Metadata
The graph will also have the context provided as metadata. This can be left untouched, if desired. If a graph is returned instead of a vector, then the context will be read from the metadata instead.
```clojure
(m/transform-file
 "resources/sample.ttl"
 (fn [graph namespaces base]
   (with-meta graph {:namespaces (assoc namespaces :ex "http://ex.com/")
                     :base base}))
 "resources/destination.ttl")
```
This is useful if just adding new entries to the graph, since `assoc` preserves metadata:
```clojure
(m/transform-file
 "resources/sample.ttl"
 (fn [graph namespaces base]
   (-> graph
       (assoc-in [(URL. "http://example.org/#green-goblin") :foaf/givenname] "Otto")
       (assoc (URL. "http://example.org/#mary-jane")
              {:a :foaf/Person
               :foaf/knows (URL. "http://example.org/#spiderman")
               :foaf/name "Mary Jane"})))
 "resources/destination.ttl")
```
### Default Contexts
If no `base` or no `namespaces` map are returned then the original `base` and `namespaces` will be used. This can be prevented by setting `*no-defaults*` to true:
```clojure
(binding [m/*no-defaults* true]
  (m/transform-file
   "resources/sample.ttl"
   (fn [graph _ _] [graph])
   "resources/destination.ttl"))
```
This will drop the base and namespaces from the file.

### Graph Format
The graph is read into a nested map. The demo file will be parsed as the following Clojure structure:
```clojure
{(URI. "http://example.org/#green-goblin")
   {:rel/enemyOf (URI. "http://example.org/#spiderman")
    :a :foaf/Person,
    :foaf/name "Green Goblin"},
 (URI. "http://example.org/#spiderman")
   {:rel/enemyOf (URI. "http://example.org/#green-goblin"),
    :a :foaf/Person,
    :foaf/name #{"Spiderman"
                 (map->LangLiteral {:text "Человек-паук" :lang "ru"})}}}
```
Where `URI` is `java.lang.URI` and `LangLiteral` is `donatello.ttl.LangLiteral`. Note that the prefixes and base are attached as metadata.

The keys of the graph are the subjects, with each subject being assoc'ed with a map of predicate/objects. If a predicate maps a set of objects, then that implies multiple triples for that subject/predicate. In the provided example, the `#spiderman` subject has 2 names: `"Spiderman"` and `"Человек-паук"@ru`. This is the same as the pair of triples:
```ttl
<#spiderman> foaf:name "Spiderman" .
<#spiderman> foaf:name "Человек-паук"@ru .
```

### Clojure RDF Objects
Literals are usually returned as their natural data type (strings, longs, double), except literals with language tags, or typed literals. These will be returned using `RuDolF` types:
- `quoll.rdf.LangLiteral` for language tagged strings. These have fields of `:text` and `:lang`.
- `quoll.rdf.TypedLiteral` for typed literals. These have fields of `:text` and `:type`, where the `:type` will be a `URL` or keyword representing a QName.
Another type of object that may be returned by the parser is:
- `quoll.rdf.BlankNode` for blank nodes. These have an `:id` field to distinguish them.

## License

Copyright © 2023 Paula Gearon

Distributed under the Eclipse Public License version 2.0.
