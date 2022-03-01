(ns com.phronemophobic.snowball
  (:require [clojure.tools.build.api :as b]
            clojure.tools.deps.alpha
            [treemap-clj.core :as treemap]
            [clojure.zip :as z]
            [membrane.ui :as ui]
            [clojure.data.json :as json]
            ;; (require 'membrane.java2d)
            [treemap-clj.rtree :as rtree]
            [membrane.component :as component])
  (:import com.github.davidmoten.rtree.geometry.Geometries
           com.github.davidmoten.rtree.RTree)
  (:gen-class))


(defn human-readable [size]
  (some (fn [[num suffix]]
          (when (>= size num)
            (let [coefficient (double (/ size num))
                  num-str (if (< coefficient 10)
                            (format "%.1f" coefficient)
                            (-> coefficient (Math/round) int))]
              (str num-str suffix))))
        [[1e12 "T"]
         [1e9 "G"]
         [1e6 "M"]
         [1e3 "k"]
         [1 ""]]))

(defn overlaps? [rt [x y w h]]
  (-> (.search ^RTree rt (Geometries/rectangle
                          (double x) (double y)
                          (double (+ x w)) (double (+ y h))))
      (.toBlocking)
      (.toIterable)
      seq))



(defn rects->absolute [tm]
  (let [zip (treemap/treezip tm)]
    (loop [zip zip]
      (if (z/end? zip)
        (z/root zip)
        (recur (-> zip
                   (z/edit (fn [rect]
                             (let [x (if (z/end? zip)
                                       0
                                       (loop [x 0
                                              zip zip]
                                         (if-not zip
                                           x
                                           (recur (+ x (:x (z/node zip)))
                                                  (z/up zip)))))
                                   y (if (z/end? zip)
                                       0
                                       (loop [y 0
                                              zip zip]
                                         (if-not zip
                                           y
                                           (recur (+ y (:y (z/node zip)))
                                                  (z/up zip)))))]
                               (assoc rect
                                      :ax x
                                      :ay y))))
                   (z/next)))))))

(defn render-labels [tm]
  (let [tm (rects->absolute tm)
        rects (reverse (sort-by #(-> % :obj :size) (tree-seq :children :children tm)))

        [rtree labels]
        (reduce
         (fn [[rt labels] rect]
           (let [label (ui/label
                        (clojure.string/join "\n"
                                             [(human-readable (-> rect :obj (:size 0)))
                                              (-> rect :obj :name name)])
                        (ui/font "monospace" 12))
                 [w h] (ui/bounds label)
                 x (:ax rect)
                 y (:ay rect)]
             (if (overlaps? rt [x y w h])
               [rt labels]
               [(rtree/add! rt {:x x
                                :y y
                                :w w
                                :h h})
                (conj labels
                      (ui/with-color [1 1 1 0.2]
                        [(ui/translate (inc x) (inc y)
                                       label)
                         (ui/translate (inc x) (dec y)
                                       label)
                         (ui/translate (dec x) (dec y)
                                       label)
                         (ui/translate (dec x) (inc y)
                                       label)])

                      (ui/translate x y
                                    label)
                      )])))
         [(rtree/rtree)
          []]
         rects)]
    labels)) 



(defn coord-size [lib-tree {:keys [children paths]
                            :as coord}]
  (transduce (comp (map clojure.java.io/file)
                   (map #(.length %)))
             +
             0
             paths))

(defn transitive-coord-size [lib-tree {:keys [children paths]
                                       :as coord}]
  (let [transitive-coords (tree-seq :children
                                    (fn [coord]
                                      (map #(get lib-tree %) (:children coord)))
                                    coord)]
    (transduce (map :size)
               +
               0
               transitive-coords)))

(defn top-level-coord? [coord]
  (-> coord :dependents nil?))

(defn top-level-deps [lib-tree]
  (->> lib-tree
       (keep (fn [[lib coord]]
               ;; based on clojure.tools.deps.alpha/print-tree
               ;; implementation for finding root deps
               (when (top-level-coord? coord)
                 lib)))))

(defn root-coord [lib-tree]
  (let [coord {:children (top-level-deps lib-tree)
               :name 'root/root
               ;; don't try co calculate root deps since
               ;; they're most likely unzipped
               :size 0
               :paths []}]
    (assoc coord
           :transitive-size (transitive-coord-size lib-tree coord))))

(defn basis->size-tree [basis]
  ;; if clojure.tools.deps.alpha/make-tree breaks,
  ;; check clojure.tools.deps.alpha/print-tree
  (let [tree (#'clojure.tools.deps.alpha/make-tree (:libs basis))
        tree+names+sizes (into
                          {}
                          (map (fn [[k v]]
                                 (let [size (coord-size tree v)]
                                   [k (assoc v
                                             :name k
                                             :top-level (top-level-coord? v)
                                             :size-readable (human-readable size)
                                             :size size)])))
                          tree)
        tree+names+sizes+transitive-sizes
        (into
         {}
         (map (fn [[k v]]
                (let [transitive-size (transitive-coord-size tree+names+sizes v)]
                 [k (assoc v
                           :transitive-size transitive-size
                           :transitive-size-readable (human-readable transitive-size))])))
         tree+names+sizes)]
    tree+names+sizes+transitive-sizes))

(defn render-depth
  "Draw filled rectangles of leaf rects
  with colors corresponding to the depth."
  ([rect]
   (render-depth rect 0.2))
  ([rect opacity]
   (let [mdepth (treemap/max-depth rect)
         color-gradient (requiring-resolve 'treemap-clj.view/color-gradient)]
     (loop [to-visit (seq [[0 0 0 rect]])
            view []]
       (if to-visit
         (let [[depth ox oy rect] (first to-visit)
               to-visit (if-let [children (:children rect)]
                          (let [ox (+ ox (:x rect))
                                oy (+ oy (:y rect))]
                            (into (next to-visit)
                                  (map #(vector (inc depth) ox oy %) children)))
                          (next to-visit))]
           (recur to-visit
                  (conj view
                        (let [children? (:children rect)
                              opacity (if children?
                                        ;;(max 0.2 (- 0.8 (* depth 0.2)))
                                        0.2
                                        opacity)
                              style (if children?
                                      :membrane.ui/style-stroke
                                      :membrane.ui/style-fill)]
                          (ui/with-style style
                            (ui/with-stroke-width 2
                              (ui/translate (+ (:x rect) ox) (+ (:y rect) oy)
                                            [(ui/with-color (conj (if children?
                                                                    [0 0 0]
                                                                    (color-gradient (/ depth mdepth))
                                                                    )
                                                                  opacity)
                                               (ui/rectangle (max 1 (dec (:w rect)))
                                                             (max 1 (dec (:h rect))))
                                               )]))))))
           )
         view)))))

(defn basis->treemap [basis]
  (let [lib-tree (basis->size-tree basis)
        tm (treemap/treemap (root-coord lib-tree)
                            (treemap/make-rect 600 600)
                            (merge
                             treemap/treemap-options-defaults
                             {:size (fn [coord]
                                      (max 1 (:transitive-size coord) ))
                              :keypath-fn
                              (fn [coord]
                                (cons
                                 '(find self)
                                 (map #(list 'find %)
                                      (:children coord))))
                              :branch? #(-> % :children seq)
                              :children (fn [coord]
                                          (cons
                                           {:name (symbol
                                                   (namespace (:name coord))
                                                   (str "self:"
                                                        (name (:name coord))))
                                            :size (:size coord)
                                            :transitive-size (:size coord)}
                                           (->> coord
                                                :children
                                                (map #(get lib-tree %)))))}))]
    tm))

(defn render-treemap [tm]
  (let [rendered [(render-depth tm)
                  (render-labels tm)]]
   ((requiring-resolve 'treemap-clj.view/wrap-treemap-events) tm rendered)))

(defn size-treemap [basis]
  ((requiring-resolve 'membrane.java2d/run-sync)
   (component/make-app (requiring-resolve 'treemap-clj.view/treemap-explore) {:tm-render (-> (basis->treemap basis)
                                                                                             (render-treemap))})
   {:window-start-width 1350
    :window-start-height 700
    :window-title "Snowball"}))

(defn treemap-image [basis fname]
  ((requiring-resolve 'membrane.java2d/save-to-image!)
   fname
   (-> (basis->treemap basis)
       (render-treemap)))
  (println "Saved to " fname "."))

(defn treemap-edn [basis]
  (binding [*print-length* false]
    (prn (clojure.walk/prewalk
          (fn [obj]
            (if (record? obj)
              (into {} obj)
              obj))
          (basis->treemap basis)))))

(defn treemap-json [basis]
  (json/write (basis->treemap basis) *out*))

(defn opts->basis [{version :mvn/version
                    sha :git/sha
                    url :git/url
                    root :local/root
                    deps :deps

                    lib :lib}]
  (when (not deps)
    (assert lib "Lib coordinate is required"))
  (when sha
    (assert url ":git/sha provided, but :git/url not provided"))
  (when url
    (assert sha ":git/url provided, but :git/sha not provided"))
  (let [deps (cond
               version {:deps {lib {:mvn/version (name version)}}}
               sha {:deps {lib {:git/sha (name sha)
                                :git/url (name url)}}}
               root {:deps {lib {:local/root (name root)}}}
               deps (name deps)
               :else {:deps {lib {:mvn/version "RELEASE"}}})]
    (b/create-basis {:project deps})))

(defn print-sizes [basis]
  (let [lib-tree (basis->size-tree basis)
        top-libs (->> lib-tree ;; (select-keys lib-tree (top-level-deps lib-tree))
                      vals
                      (sort-by :size)
                      reverse)
        max-namespace-width (->> top-libs
                                 (map :name)
                                 (map namespace)
                                 (map count)
                                 (apply max))
        max-name-width (->> top-libs
                            (map :name)
                            (map name)
                            (map count)
                            (apply max))
        header (clojure.string/join " | " [(format (str "%" max-namespace-width "s") "namespace")
                                           (format (str "%" max-name-width "s") "name")
                                           (format (str "%13s") "transitive-size")
                                           (format (str "%13s") "self-size")])
        ]
    (println header)
    (println (clojure.string/join (repeat (count header) "-" )))
    (doseq [{:keys [transitive-size size name]} top-libs]
      (println (clojure.string/join " | " [(format (str "%" max-namespace-width "s") (namespace name))
                                           (format (str "%" max-name-width "s") (clojure.core/name name))
                                           (format (str "%,15d") transitive-size)
                                           (format (str "%,13d") size)])))))

(defn print-csv [basis]
  (let [lib-tree (basis->size-tree basis)
        top-libs (->> lib-tree
                      vals
                      (sort-by :size)
                      reverse)
        columns [
                 ["namespace" #(-> % :name namespace)]
                 ["name" #(-> % :name name)]
                 ["transitive-size" :transitive-size]
                 ["transitive-size-readable" :transitive-size-readable]
                 ["self-size" :size]
                 ["self-size-readable" :size-readable]]
        ]
    (println (clojure.string/join "," (map first columns)))
    (doseq [lib top-libs]
      (println (clojure.string/join "," (map (fn [[_column-name f]]
                                               (str (f lib)))
                                             columns))))))

(defn print-edn [basis]
  (binding [*print-length* false]
    (prn (basis->size-tree basis))))

(defn print-json [basis]
  (json/write (basis->size-tree basis) *out*))

(defn print-usage []
  (print (slurp ((requiring-resolve 'clojure.java.io/resource) "usage.txt"))))

(defn -main [{:keys [view path lib deps]
              :or {view :treemap
                   path "snowball.png"}
              :as m}]
  (if (or lib deps)
    (case (name view)
      "treemap" (size-treemap (opts->basis m))
      "treemap-image" (treemap-image (opts->basis m) (str path))
      "print" (print-sizes (opts->basis m))
      "csv" (print-csv (opts->basis m))
      "edn" (print-edn (opts->basis m))
      "json" (print-json (opts->basis m))
      "treemap-edn" (treemap-edn (opts->basis m))
      "treemap-json" (treemap-json (opts->basis m))
      ;; else
      (print-usage))
    (print-usage))
  
  )
