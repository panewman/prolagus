(ns prolagus.operator.impl
  (:require [malli.core :as ml]
            [prolagus :as p]
            [prolagus.system :as ps]))

(defn- from-work
  [{:keys [::p/from] :as state}]
  (if (seq from)
    {::p/token (ps/token-next (first from)) ::p/state (assoc state ::p/from (rest from)) ::p/continue-work from-work}
    {::p/token (ps/token-complete) ::p/state (assoc state ::p/from (rest from))}))

(ml/=> from-work [:function
                  [:=> [:cat [:map [::p/from [:sequential any?]]]]
                   [:map [::p/token ps/type-token]
                    [::p/state [:map [::p/from [:sequential any?]]]]
                    [::p/continue-work {:optional true} [:fn fn?]]]]])

(defn from-init
  [config]
  (ps/init-observable-0! config from-work))

(ml/=> from-init [:function
                  [:=> [:cat [:map [::p/from [:sequential any?]]]]
                   [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])

(defn- interval-work
  [{:keys [::p/period ::p/counter] :as state}]
  (let [token (when (> counter -1) {::p/token (ps/token-next counter)})]
    (merge {::p/state (assoc state ::p/counter (inc counter)) ::p/sleep period ::p/continue-work interval-work} token)))

(ml/=> interval-work [:function
                      [:=> [:cat [:map [::p/period :int] [::p/counter :int]]]
                       [:map [::p/token {:optional true} ps/type-token]
                        [::p/state [:map [::p/period :int] [::p/counter :int]]]
                        [::p/sleep :int]
                        [::p/continue-work {:optional true} [:fn fn?]]]]])

(defn interval-init
  [config]
  (ps/init-observable-0! (assoc config ::p/counter -1) interval-work))

(ml/=> interval-init [:function
                      [:=> [:cat [:map [::p/period :int] [::p/sub-fn fn?]]]
                       [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])

(defn- buffer-count-work
  [{:keys [::p/size ::p/start-every ::p/buffer ::p/last-token] :as state} t]
  (cond
    (ps/token-next? t) (let [buf (->> (concat buffer [(::p/value t)]) (take-last size))
                             {:keys [::p/token] :as result-token} (when (= (count buf) size) {::p/token (ps/token-next buf)})
                             state (if result-token (assoc state ::p/buffer (drop start-every buf) ::p/last-token token)
                                                    (assoc state ::p/buffer buf ::p/last-token nil))]
                         (merge {::p/state state ::p/continue-work buffer-count-work} result-token))
    (ps/token-error? t) {::p/state state ::p/token t ::p/continue-work buffer-count-work}
    (ps/token-complete? t) {::p/state state ::p/token (concat (when true #_(and (nil? last-token) (seq buffer)) [(ps/token-next buffer)]) [t])}))

(ml/=> buffer-count-work [:function
                          [:=> [:cat [:map [::p/size :int] [::p/start-every :int] [::p/buffer [:sequential :any]]] ps/type-token]
                           [:map [::p/token {:optional true} [:or ps/type-token [:+ ps/type-token]]]
                            [::p/state [:map [::p/size :int] [::p/start-every :int] [::p/buffer [:sequential :any]]]]
                            [::p/continue-work {:optional true} [:fn fn?]]]]])

(defn buffer-count-init
  [config]
  (ps/init-observable-1! (assoc config ::p/buffer []) buffer-count-work))

(ml/=> buffer-count-init [:function
                          [:=> [:cat
                                [:map [::p/size :int] [::p/start-every :int] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]
                           [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])

(defn- combine-latest-work
  [{:keys [::p/last-tokens ::p/values ::p/live?] :as state} {:keys [::p/value] :as t}]
  (let [{:keys [::p/id]} (meta t)
        complete? (and (ps/token-complete? t) (every? ps/token-complete? (vals last-tokens)))
        live? (or live? (every? ps/token? (vals last-tokens)))
        {:keys [::p/values ::p/live?] :as result-values} (if (ps/token-next? t) {::p/values (assoc values id value) ::p/live? live?} nil)
        token (cond complete? {::p/token (ps/token-complete)}
                    live? {::p/token (ps/token-next values)}
                    (ps/token-error? t) {::p/token t}
                    :else nil)
        continue-work (when (not complete?) {::p/continue-work combine-latest-work})]
    #_(clojure.pprint/pprint ["combine-latest-work" (ps/token-next t) values (every? some? values) last-tokens])
    (merge {::p/state (merge state result-values)} token result-values continue-work)))

(ml/=> combine-latest-work [:function
                            [:=> [:cat [:map [::p/last-tokens [:map-of :any [:or ps/type-token :nil]]] [::p/values :any]] ps/type-token]
                             [:map [::p/token {:optional true} ps/type-token]
                              [::p/state [:map [::p/values :any]]]
                              [::p/continue-work {:optional true} [:fn fn?]]]]])

(defn combine-latest-init
  [{:keys [::p/<observables] :as config}]
  (ps/init-observable-x! (assoc config ::p/values (reduce-kv (fn [m k _] (assoc m k nil)) <observables <observables) ::p/live? false)
                         combine-latest-work))

(ml/=> combine-latest-init [:function
                            [:=> [:cat [:map [::p/<observables [:or
                                                                [:+ [:map [::p/sub-fn [:fn fn?]]]]
                                                                [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]]
                                        [::p/sub-fn fn?]]]
                             [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])

(defn- concat-work
  [state t]
  (merge {::p/state state ::p/token t} (when (not (ps/token-complete? t)) {::p/continue-work concat-work})))

(ml/=> concat-work [:function
                    [:=> [:cat [:map] ps/type-token]
                     [:map [::p/token ps/type-token]
                      [::p/state [:map]]
                      [::p/continue-work {:optional true} [:fn fn?]]]]])

(defn concat-init
  [config]
  (ps/init-observable-xs! config concat-work))

(ml/=> concat-init [:function
                    [:=> [:cat [:map [::p/<observables [:or
                                                        [:+ [:map [::p/sub-fn [:fn fn?]]]]
                                                        [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]]
                                [::p/sub-fn fn?]]]
                     [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])

(defn- filter-work
  [{:keys [::p/filter-fn] :as state} {:keys [::p/value] :as t}]
  (let [filter? (cond (ps/token-next? t) (filter-fn value)
                      (ps/token? t) true
                      :else false)
        token (when filter? {::p/token t})]
    (merge {::p/state state ::p/continue-work filter-work} token)))

(ml/=> filter-work [:function
                    [:=> [:cat [:map [::p/filter-fn [:fn fn?]]] ps/type-token]
                     [:map [::p/token {:optional true} ps/type-token]
                      [::p/state [:map [::p/filter-fn [:fn fn?]]]]
                      [::p/continue-work [:fn fn?]]]]])

(defn filter-init
  [config]
  (ps/init-observable-1! config filter-work))

(ml/=> filter-init [:function
                    [:=> [:cat [:map [::p/filter-fn [:fn fn?]] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]
                     [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])

(defn- map-work
  [{:keys [::p/map-fn] :as state} t]
  {::p/token (ps/apply-token map-fn t) ::p/state state ::p/continue-work map-work})

(ml/=> map-work [:function
                 [:=> [:cat [:map [::p/map-fn [:fn fn?]]] ps/type-token]
                  [:map [::p/token ps/type-token]
                   [::p/state [:map [::p/map-fn [:fn fn?]]]]
                   [::p/continue-work {:optional true} [:fn fn?]]]]])

(defn map-init
  [config]
  (ps/init-observable-1! config map-work))

(ml/=> map-init [:function
                 [:=> [:cat [:map [::p/map-fn [:fn fn?]] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]
                  [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])

(defn- merge-work
  [{:keys [::p/last-tokens] :as state} t]
  (let [merge-complete? (and (ps/token-complete? t) (every? ps/token-complete? (vals last-tokens)))
        token (cond merge-complete? {::p/token (ps/token-complete)}
                    (ps/token? t) {::p/token t}
                    :else nil)
        continue-work (when (not merge-complete?) {::p/continue-work merge-work})]
    (merge {::p/state state} token continue-work)))

(ml/=> merge-work [:function
                   [:=> [:cat [:map [::p/last-tokens [:map-of :keyword [:or ps/type-token :nil]]]]
                         [:or ps/type-token :nil]]
                    [:map [::p/token {:optional true} ps/type-token]
                     [::p/state [:map]]
                     [::p/continue-work {:optional true} [:fn fn?]]]]])

(defn merge-init
  [config]
  (ps/init-observable-x! config merge-work))

(ml/=> merge-init [:function
                   [:=> [:cat [:map
                               [::p/<observables [:or [:+ [:map [::p/sub-fn [:fn fn?]]]]
                                                  [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]]
                               [::p/sub-fn fn?]]]
                    [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])

(defn- take-work
  [{:keys [::p/count] :as state} t]
  (let [state (assoc state ::p/count (dec count))
        token (if (> count 1) t [t (ps/token-complete)])
        continue-work (when (> count 1) {::p/continue-work take-work})]
    (merge {::p/token token ::p/state state} continue-work)))

(ml/=> take-work [:function
                  [:=> [:cat [:map [::p/count :int]] ps/type-token]
                   [:map [::p/token [:or ps/type-token [:sequential ps/type-token]]]
                    [::p/state [:map [::p/count :int]]]
                    [::p/continue-work {:optional true} [:fn fn?]]]]])

(defn take-init
  [config]
  (ps/init-observable-1! config take-work))

(ml/=> take-init [:function
                  [:=> [:cat [:map [::p/count :int] [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]]]
                   [:map [::p/unsub-fn [:fn fn?]] [::p/<chan ps/type-chan]]]])
