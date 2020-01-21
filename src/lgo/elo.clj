(ns lgo.elo
  "Function for calculating Elo points."
  (:require [clojure.math.numeric-tower :as math]))

(def K 32)

(defn Q [R]
  (math/expt 10 (/ R 400)))

(defn EA [RA RB]
  (let [QA (Q RA)
        QB (Q RB)]
    (/ QA (+ QA QB))))

(defn rating-adjustment [SA EA]
  (math/round (* K (- SA EA))))

(defn new-rating [RA RB result]
  (+ RA (rating-adjustment result (EA RA RB))))

(defn process-games [players games]
  (reduce (fn [plyrs {b :b w :w r :r}]
            (let [Rb (plyrs b)
                  Rw (plyrs w)
                  S (if ( = (first r) \b) 1.0 0.0)
                  Db (rating-adjustment S (EA Rb Rw))]
              (println Db)
              (-> plyrs
                  (update-in [b] + Db)
                  (update-in [w] - Db))))
          players
          games))

(def players
  {"A" 1200
   "B" 1200})

(def games
  [
   {:b "A" :w "B" :r "b+12.5"}
   {:b "A" :w "B" :r "w+12.5"}
   {:b "A" :w "B" :r "b+12.5"}
   {:b "A" :w "B" :r "b+12.5"}
   {:b "A" :w "B" :r "b+12.5"}
   ])

(process-games players games)
