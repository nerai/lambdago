(ns lgo.analysis.lizzie
  "Functions for working with the output of Lizzie after doing KataGo analysis.

  Design decisions:
  Internally all score values are from Black's perspective: positive means Black
  win, negative means White win.
  Move counter tells how many moves were made. Color tells whose turn is it.

  The raw data is a hash-map with keys color, move, mean, meanmean, medianmean,
  means.
  "
  (:require [clojure.string :as string]
            [clojure.core.matrix.stats :refer [mean]]
            [lgo.sgf :refer [flat-list-properties
                             extract-properties
                             extract-single-value]]))

(def B<->W {"B" "W", "W" "B"})

(defn median
  "Calculating the median for a collection of numerical values."
  [nums]
  (let [ordered (vec (sort nums))
        n (count nums)
        h (dec (int (/ n 2)))
        indices (if (odd? n)
                  [(inc h)]
                  [h (inc h)])]
    (mean (map ordered indices))))

(defn extract-from-LZ
  "Simply extracts from LZ string s the values after the tag.
  If the tag has more values (like PV), this gets only the first."
  [s tag]
  (map second
       (filter #(= tag (first %))
               (partition 2 1
                          (clojure.string/split s #" ")))))

(defn raw-data
  "Extracts the score means and the color of the previous move from
  Katago-Lizzie output."
  [flp]
  (let [x (map (fn [[id val]]
                 (if (#{"B" "W"} id)
                   id
                   (mapv read-string ;(comp (partial * -1) read-string)
                        (extract-from-LZ val "scoreMean"))))
               (extract-properties flp #{"B" "W" "LZ"}))
        y (partition 2 x)] ;combining move and score mean
    (map (fn [[player means] move]
           (let [meanz (if (= player "W")  ;all values form Black's perspective
                         means
                         (map (partial * -1) means))]
             {:move move
              :color (B<->W player)
              :mean (first meanz)
              :meanmean (mean meanz)
              :medianmean (median meanz)
              :means meanz}))
         y (iterate inc 1)))) ;counting the moves from 1

(defn unroll-scoremeans
  "All score means from raw data. This is just unrolling the means vector
  into separate rows."
  [dat]
  (mapcat
   (fn [d]
     (for [m (:means d)]
       {:color (:color d)
        :move (:move d)
        :mean (if (= "B" (:color d))
                m
                (- m))}))
   dat))

(defn effects
  "The score mean differences caused by the moves."
  [dat]
  (map (fn [[{c1 :color m1 :mean}
             {m2 :mean v2 :move}]]
         (let [eff (if (= c1 "B") ; need to negate for White
                     (- m2 m1)
                     (- (- m2 m1)))]
           {:color c1 :effect eff :move v2}))
       (partition 2 1 dat)))

(defn choices
  [dat]
  (let [ps (partition 2 1 dat)]
    (map (fn [[{c1 :color  m1 :mean mm :meanmean md :medianmean v1 :move}
               {c2 :color m2 :mean v2 :move}]]
           (if (= "B" c1)
             {:color c1 :choice m2 :move v1 :average mm :median md :AI m1}
             {:color c1 :choice (- m2) :move v1 :average (- mm) :median (- md) :AI (- m1)}
             ))
         ps)))

(defn deviations
  [effs]
  (let [avg (mean (map :effect effs))]
    (map (fn [{e :effect :as d}]
           (into d [[:deviation (- e avg)]]))
         effs)))


(defn data-transform
  "Prepares database for plotting in the same diagram. Fixed keys are copied,
  then the variable ones are added as 'name' and its value under kw."
  [db fixedkeys varkeys kw]
  (mapcat (fn [row]
            (let [fixedvals (into {} (map (fn [k] [k (k row)]) fixedkeys))]
              (map (fn [k] (into fixedvals
                                 [[:name (name k)] [kw (k row)]]))
                   varkeys)))
          db))

;; Oz visualization functions producing vega-lite specifications
(defn oz-effects
  [e-d w t]
  {:data {:values e-d}
   :layer[{:encoding {:x {:field "move" :type "ordinal"}
                        :y {:field "effect" :type "quantitative"} :color {:field "color" :type "nominal"}}
             :mark "bar" :width w :title t}
          {:encoding {
                        :y {:field "effect" :type "quantitative" :aggregate "mean"} :color {:field "color" :type "nominal"}}
             :mark "rule"}
            ]})

(defn oz-deviations
  [e-d w t]
  {:data {:values e-d}
   :vconcat[{:encoding {:x {:field "move" :type "ordinal"}
                        :y {:field "deviation" :type "quantitative"}}
             :mark "bar" :width w :title t}]})

(defn oz-normalized-effects
  [e-d w t]
  (let [N (count e-d)
        cmsm (reductions + (map :effect e-d))
        normalized (map (fn [d v] (into d [[:cumsum (/ v (/ (:move d) 2))]]))
                     e-d cmsm)]
    {:data {:values normalized}
     :encoding {:x {:field "move" :type "quantitative"}
                :y {:field "cumsum" :type "quantitative"} :color {:field "color" :type "nominal"}}
     :mark "bar" :width w :title t}))

(defn oz-effects-summary
  [e-d]
  {:data {:values e-d}
   :title "Summary of effects"
   :encoding {:x {:field "color" :type "nominal"}
              :y {:field "effect" :type "quantitative"}}
   :mark {:type "boxplot" :extent "min-max"}})

(defn oz-all-scoremeans
  [d w t]
  {:data {:values d}
   :width w
   :title t
   :encoding {:x {:field "move" :type "ordinal"}
              :y {:field "mean" :type "quantitative"}}
   :mark {:type "boxplot" :extent "min-max" :size 5}})


(defn oz-choices
  [c w t]
  {:data {:values c}
   :encoding {:x {:field "move" :type "ordinal"}
              :y {:field "scoreMean" :type "quantitative"}
              :color {:field "name" :type "nominal"}}
   :mark {:type "line" :size 1}  :width w :title t })

(defn sgf-report
  [sgf]
  (let [flp (flat-list-properties sgf)
        black (extract-single-value flp "PB")
        white (extract-single-value flp "PW")
        result (extract-single-value flp "RE")
        raw (raw-data flp)
        all-sm (unroll-scoremeans raw)
        effs-dat (effects raw)
        dev-dat (deviations effs-dat)
        cs (choices raw)
        tcs (data-transform cs [:color :move]
                            [:choice :median :AI :average] :scoreMean)
        N (count effs-dat)
        w (int (* 5.4 N))]
    [:div
     [:h1 (str "B: " black " W: " white) " R: " result]
     [:p "Move numbers for score means indicate how many moves made before."]
     [:vega-lite (oz-choices
                  (filter #(= "B" (:color %)) tcs)
                  w
                  "Black's scoremean values")]
     [:vega-lite (oz-choices
                  (filter #(= "W" (:color %)) tcs)
                  w
                  "White's scoremean values")]
     [:vega-lite (oz-all-scoremeans
                  (filter #(= "B" (:color %)) all-sm)
                  w
                  "Black's all scoreMeans for variations")]
     [:vega-lite (oz-all-scoremeans
                  (filter #(= "W" (:color %)) all-sm)
                  w
                  "White's all scoreMeans for variations")]
     [:vega-lite (oz-effects effs-dat w "Effects of moves")]
     [:vega-lite (oz-effects (filter #(= "W" (:color %)) effs-dat) w "Effects of White's moves")]
     [:vega-lite (oz-effects (filter #(= "B" (:color %)) effs-dat) w "Effects of Black's moves")]
     [:vega-lite (oz-deviations (filter #(= "W" (:color %)) dev-dat) w "Deviations (distances from the mean) of White's moves")]
     [:vega-lite (oz-deviations (filter #(= "B" (:color %)) dev-dat) w "Deviations of Black's moves")]
     [:vega-lite
      (oz-normalized-effects (filter #(= "W" (:color %)) effs-dat)  w
                             "White's Cumulative sum of effects normalized by number of moves made")]
     [:vega-lite
      (oz-normalized-effects (filter #(= "B" (:color %)) effs-dat)  w
                             "Black's Cumulative sum of effects normalized by number of moves made")]
     [:div {:style {:display "flex" :flex-direction "row"}}
      [:vega-lite (oz-effects-summary effs-dat)]]
     [:p "Report generated by LambdaGo v" (System/getProperty "lambdago.version")]]))