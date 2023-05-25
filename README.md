# michelangelo
TTL transforming library.

## deps.edn
Add the following to the `:deps` map in `deps.edn`:

```clojure
io.github.quoll/michelangelo {:git/tag "v0.0.1" :git/sha "c5f5a48"}
```

## Usage
This library uses [Raphael](https://github.com/quoll/raphael) to parse [Turtle](https://www.w3.org/TR/turtle/) into an ordered nested map, where it can be modified, appended to, etc. These structures can then be written to new Turtle files using [Donatello](https://github.com/quoll/donatello):

```clojure
(require '[michelangelo.core :as m])

(let [graph (m/parse (slurp "source.ttl"))
      context (meta graph)]

  ;; context is a map of :namespaces and :base
  ;; update to include a new prefix
  ;; add new triples that uses this prefix
  (let [new-context (update context :namespaces assoc :ex "http://ex.com/")
        new-graph (assoc graph :ex/subject {:ex/predicate "object 1"
                                            :ex/predicate2 #{1 2 3}})]

    ;; The context should be added back as meta, just as it arrived
    (with-open [out (io/writer "destination.ttl")]
      (m/write-graph out (with-meta new-graph new-context)))))
```

## License

Copyright © 2023 Paula Gearon

Distributed under the Eclipse Public License version 2.0.
