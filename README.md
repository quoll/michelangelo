# michelangelo
TTL transforming library.

## deps.edn
Add the following to the `:deps` map in `deps.edn`:

```clojure
io.github.quoll/michelangelo {:git/tag "v0.1.0" :git/sha "d7d1b29"}
```

## Usage
This library uses [Raphael](https://github.com/quoll/raphael) to parse [Turtle](https://www.w3.org/TR/turtle/) into an ordered nested map, where it can be modified, appended to, etc. These structures can then be written to new Turtle files using [Donatello](https://github.com/quoll/donatello):

```clojure
(require '[michelangelo.core :as m])
(transform-file
  "resources/sample.ttl"
  (fn [graph namespaces base]
    [(assoc graph :ex/subject {:ex/predicate "object 1"
                               :ex/predicate2 #{1 2 3}})
     (assoc namespaces :ex "http://ex.com/")
     base])
  "resources/destination.ttl")
```

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

## License

Copyright © 2023 Paula Gearon

Distributed under the Eclipse Public License version 2.0.
