(ns prolagus.operator
  (:refer-clojure :exclude [concat filter map merge take])
  (:require [malli.core :as ml]
            [prolagus :as p]
            [prolagus.operator.impl :as pi]))

(defn from
  [coll]
  {::p/from coll
   ::p/sub-fn pi/from-init})

(ml/=> from [:function
             [:=> [:cat [:sequential any?]]
              [:map [::p/from [:sequential any?]] [::p/sub-fn fn?]]]])

(defn interval
  [period]
  {::p/period period
   ::p/sub-fn pi/interval-init})

(ml/=> interval [:function
                 [:=> [:cat :int]
                  [:map [::p/period :int] [::p/sub-fn fn?]]]])

(defn buffer-count
  ([size <observable]
   (buffer-count size 1 <observable))
  ([size start-every <observable]
   {::p/size size
    ::p/start-every start-every
    ::p/<observable <observable
    ::p/sub-fn pi/buffer-count-init}))

(ml/=> buffer-count [:function
                     [:=> [:cat :int [:map [::p/sub-fn [:fn fn?]]]]
                      [:map [::p/size :int] [::p/start-every :int] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]
                     [:=> [:cat :int :int [:map [::p/sub-fn [:fn fn?]]]]
                      [:map [::p/size :int] [::p/start-every :int] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]])

(defn combine-latest
  [observables]
  {::p/<observables observables
   ::p/sub-fn pi/combine-latest-init})

(ml/=> combine-latest [:function
                       [:=> [:cat [:or [:+ [:map [::p/sub-fn [:fn fn?]]]] [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]]
                        [:map [::p/<observables [:or [:+ [:map [::p/sub-fn [:fn fn?]]]] [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]] [::p/sub-fn fn?]]]])

(defn concat
  [observables]
  {::p/<observables observables
   ::p/sub-fn pi/concat-init})

(ml/=> concat [:function
               [:=> [:cat [:or [:+ [:map [::p/sub-fn [:fn fn?]]]] [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]]
                [:map [::p/<observables [:or [:+ [:map [::p/sub-fn [:fn fn?]]]] [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]] [::p/sub-fn fn?]]]])

(defn filter
  [filter-fn <observable]
  {::p/filter-fn filter-fn
   ::p/<observable <observable
   ::p/sub-fn pi/filter-init})

(ml/=> filter [:function
               [:=> [:cat [:fn fn?] [:map [::p/sub-fn [:fn fn?]]]]
                [:map [::p/filter-fn [:fn fn?]] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]])

(defn map
  [map-fn <observable]
  {::p/map-fn map-fn
   ::p/<observable <observable
   ::p/sub-fn pi/map-init})

(ml/=> map [:function
            [:=> [:cat [:fn fn?] [:map [::p/sub-fn [:fn fn?]]]]
             [:map [::p/map-fn [:fn fn?]] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]])

(defn merge
  [observables]
  {::p/<observables observables
   ::p/sub-fn pi/merge-init})

(ml/=> merge [:function
              [:=> [:cat [:or [:+ [:map [::p/sub-fn [:fn fn?]]]]
                          [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]]
               [:map [::p/<observables [:or [:+ [:map [::p/sub-fn [:fn fn?]]]] [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]] [::p/sub-fn fn?]]]])

(defn take
  [n <observable]
  {::p/count n
   ::p/<observable <observable
   ::p/sub-fn pi/take-init})

(ml/=> take [:function
             [:=> [:cat :int [:map [::p/sub-fn [:fn fn?]]]]
              [:map [::p/count :int] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]])

(defn tap
  [map-fn <observable]
  {::p/map-fn (fn tap-fn [v] (map-fn v) v)
   ::p/<observable <observable
   ::p/sub-fn pi/map-init})

(ml/=> tap [:function
            [:=> [:cat [:fn fn?] [:map [::p/sub-fn [:fn fn?]]]]
             [:map [::p/map-fn [:fn fn?]] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]])

(defn start-with
  [value <observable]
  {::p/<observables [(from [value]) <observable]
   ::p/sub-fn pi/concat-init})

(ml/=> start-with [:function
                   [:=> [:cat :any [:map [::p/sub-fn [:fn fn?]]]]
                    [:map [::p/<observables [:or [:+ [:map [::p/sub-fn [:fn fn?]]]] [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]] [::p/sub-fn fn?]]]])

(defn distinct-until-changed
  ([<observable]
   (distinct-until-changed = <observable))
  ([equal-fn <observable]
   {::p/map-fn identity
    ::p/<observable (->> <observable
                         (start-with nil)
                         (buffer-count 2)
                         (filter #(not (equal-fn (first %) (second %))))
                         (filter #(= 2 (count %)))
                         (map second))
    ::p/sub-fn pi/map-init}))

(ml/=> distinct-until-changed [:function
                               [:=> [:cat p/type-observable]
                                [:map [::p/map-fn [:fn fn?]] [::p/<observable p/type-observable] [::p/sub-fn fn?]]]
                               [:=> [:cat [:fn fn?] p/type-observable]
                                [:map [::p/map-fn [:fn fn?]] [::p/<observable p/type-observable] [::p/sub-fn fn?]]]])
