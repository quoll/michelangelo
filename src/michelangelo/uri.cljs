(ns michelangelo.uri
  "A set of extensions to goog.Uri to make them behave more conformantly.
  The goog.Uri class has a few problems compared to java.net.URI:
  - Identical URIs are not equal to each other
  - Identical URIs have different hash codes
  - Fragments are lost if they are defined but empty"
  {:author "Paula Gearon"}
  (:import [goog Uri]))

(def ^:private magic 160217)

(defn- nnil [s] (and (seq s) s))

(extend-type Uri
  Object
  ;; This duplicates goog.Uri.prototype.toString
  ;; but includes defined, empty fragments
  (toString [u]
    (let [scheme (nnil (.getScheme u))
          domain (nnil (.getDomain u))
          path (nnil (.getPath u))
          query (nnil (.getQuery u))
          fragment (.getFragment u)]
      (str (some-> scheme
                   (goog.Uri.encodeSpecialChars_ goog.Uri.reDisallowedInSchemeOrUserInfo_ true)
                   (str \:))
           (and (or domain (= scheme "file"))
                (str "//"
                     (some-> (nnil (.getUserInfo u))
                             (goog.Uri.encodeSpecialChars_ goog.Uri.reDisallowedInSchemeOrUserInfo_ true)
                             (str \@))
                     (and domain (goog.Uri.removeDoubleEncoding_ (goog.string.urlEncode domain)))
                     (some->> (.getPort u) (str \:))))
           (and path
                (let [relative (not= \/ (first path))]
                  (str (when (and domain relative) \/)
                       (goog.Uri.encodeSpecialChars_ path
                                                     (if relative
                                                       goog.Uri.reDisallowedInRelativePath_
                                                       goog.Uri.reDisallowedInAbsolutePath_)
                                                     true))))
           (some->> query (str \?))
           (when fragment
             (str \# (goog.Uri.encodeSpecialChars_ fragment goog.Uri.reDisallowedInFragment_))))))
  IEquiv
  (-equiv [this other]
    (and (= goog.Uri (type other))
         (= (str this) (str other))))
  IHash
  (-hash [this]
    (+ magic (hash (str this)))))


(defn uri
  "Creates a URI.
  This sets the fragment to nil if there is none, and an empty string if it is defined but empty"
  [s]
  (let [u (Uri. s)]
    (when (and (empty? (.getFragment u)) (not= \# (last s)))
      (.setFragment u nil))
    u))
