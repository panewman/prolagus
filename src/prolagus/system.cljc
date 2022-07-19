(ns prolagus.system
  (:require [clojure.core.async :as as]
            [malli.core :as ml]
            [prolagus :as p])
  #?(:clj (:import  [clojure.core.async.impl.channels ManyToManyChannel])))

#_(do (require '[malli.dev :as mldev])
      (mldev/start!))

#_(mldev/stop!)

(def type-token-next
  [:map {:title "token-next"} [::p/kind [:enum ::p/next]] [::p/value :any]])
(def type-token-error
  [:map {:title "token-error"} [::p/kind [:enum ::p/error]] [::p/error :any]])
(def type-token-complete
  [:map {:title "token-complete"} [::p/kind [:enum ::p/complete]]])
(def type-token-control
  [:map {:title "token-control"} [::p/kind [:enum ::p/control]] [::p/value :any]])
(def type-token [:or {:title "token"} type-token-next type-token-error type-token-complete type-token-control])

(defn chan?
  [c]
  #?(:clj  (instance? ManyToManyChannel c)
     :cljs (some? (.-takes c))))

(def type-chan
  [:fn chan?])

(defn token-next
  [value]
  {::p/kind ::p/next
   ::p/value value})

(ml/=> token-next [:function
                   [:=> [:cat :any]
                    type-token-next]])

(defn token-next?
  [{:keys [::p/kind]}]
  (= kind ::p/next))

(ml/=> token-next? [:function
                    [:=> [:cat :any]
                     :boolean]])

(defn token-error
  [e]
  {::p/kind ::p/error
   ::p/error e})

(ml/=> token-error [:function
                    [:=> [:cat :any]
                     type-token-error]])

(defn token-error?
  [{:keys [::p/kind]}]
  (= kind ::p/error))

(ml/=> token-error? [:function
                     [:=> [:cat :any]
                      :boolean]])

(defn token-complete
  [& _]
  {::p/kind ::p/complete})

(ml/=> token-complete [:function
                       [:=> [:cat [:* :any]]
                        type-token-complete]])

(defn token-complete?
  [{:keys [::p/kind]}]
  (= kind ::p/complete))

(ml/=> token-complete? [:function
                        [:=> [:cat :any]
                         :boolean]])

(defn token-control
  [value]
  {::p/kind ::p/control
   ::p/value value})

(ml/=> token-control [:function
                      [:=> [:cat :any]
                       type-token-control]])

(defn token-control?
  [{:keys [::p/kind]}]
  (= kind ::p/control))

(ml/=> token-control? [:function
                       [:=> [:cat :any]
                        :boolean]])

(defn token?
  [{:keys [::p/kind]}]
  (some? (#{::p/next ::p/error ::p/complete ::p/control} kind)))

(ml/=> token? [:function
               [:=> [:cat :any]
                :boolean]])

(defn apply-token
  [f {:keys [::p/kind ::p/value] :as m}]
  (if (= kind ::p/next)
    (try
      (token-next (f value))
      (catch Exception e
        (token-error e)))
    m))

(ml/=> apply-token [:function
                    [:=> [:cat [:fn fn?]
                          [:or type-token-next type-token-error type-token-complete]]
                     [:or type-token-next type-token-error type-token-complete]]])

(defn put-coll!
  [>chan coll func]
  (let [v (first coll)]
    (if (some? v)
      (as/put! >chan v (fn [ok] (if ok (put-coll! >chan (rest coll) func) (when func (func)))))
      (when func (func)))))

(ml/=> put-coll! [:function
                  [:=> [:cat type-chan [:sequential type-token] [:fn fn?]]
                   :any]])

(defn put!
  [>chan token cont-fn]
  (cond (token? token) (as/put! >chan token cont-fn)
        (coll? token) (put-coll! >chan token (partial cont-fn true))
        :else (cont-fn true)))

(ml/=> put! [:function
             [:=> [:cat type-chan [:or [:sequential type-token] type-token :nil] [:fn fn?]]
              :any]])

(defn normalize-map-coll
  "Transforms\n a map -> [[k v] [k v] ...]\n a coll -> [[0 v] [1 v] ...]"
  [map-or-coll]
  (cond (map? map-or-coll) (seq map-or-coll)
        :else (mapv #(vector %2 %1) map-or-coll (range))))

(ml/=> normalize-map-coll [:function
                           [:=> [:cat [:or [:sequential any?] [:map-of :any :any]]]
                            [:sequential [:tuple :any :any]]]])

(defn make-unsub-fn
  ([chan]
   (fn [& _]
     (as/close! chan)))
  ([chan source-unsub-fn]
   (fn [& _]
     (cond (fn? source-unsub-fn) (source-unsub-fn)
           (coll? source-unsub-fn) (doseq [f source-unsub-fn] (f))
           :else nil)
     (cond (coll? chan) (doseq [c chan] (as/close! c))
           :else (as/close! chan)))))

(ml/=> make-unsub-fn [:function
                      [:=> [:cat type-chan]
                       [:fn fn?]]
                      [:=> [:cat [:or type-chan [:* type-chan]] [:or :nil [:fn fn?] [:* [:fn fn?]]]]
                       [:fn fn?]]])

(defn make-unsub-control-fn
  []
  (let [>chan-control (as/chan)]
    {::p/unsub-control-fn (fn [] (as/put! >chan-control (token-control ::p/close) (fn [ok] (as/close! >chan-control))))
     ::p/>chan-control >chan-control}))

(ml/=> make-unsub-control-fn [:function
                              [:=> [:cat]
                               [:map [::p/unsub-control-fn [:fn fn?]] [::p/>chan-control type-chan]]]])

(defn run-observable-0!
  [>chan {:keys [::p/unsub-fn] :as state} impl-work]
  (let [{:keys [::p/token ::p/state ::p/sleep ::p/continue-work]} (impl-work state)
        cont-fn (fn [ok]
                  (if (and ok (some? continue-work))
                    (if (or (nil? token) (some? sleep))
                      (as/take! (as/timeout (or sleep 10)) (fn [& _] (run-observable-0! >chan state continue-work)))
                      (run-observable-0! >chan state continue-work))
                    (unsub-fn)))]
    (put! >chan token cont-fn)))

(ml/=> run-observable-0! [:function
                          [:=> [:cat type-chan [:map [::p/unsub-fn [:fn fn?]]] [:fn fn?]]
                           :any]])

(defn init-observable-0!
  [config impl-work]
  (let [>chan (as/chan)
        unsub-fn (make-unsub-fn >chan)
        state (merge config {::p/unsub-fn unsub-fn})]
    (run-observable-0! >chan state impl-work)
    {::p/unsub-fn unsub-fn ::p/<chan >chan}))

(ml/=> init-observable-0! [:function
                           [:=> [:cat [:map] [:fn fn?]]
                            [:map [::p/unsub-fn [:fn fn?]] [::p/<chan type-chan]]]])

(defn run-observable-1!
  [<chan >chan {:keys [::p/unsub-fn] :as state} impl-work]
  (as/take!
    <chan
    (fn [t]
      (if (some? t)
        (let [{:keys [::p/token ::p/state ::p/sleep ::p/continue-work]} (impl-work state t)
              cont-fn (fn [ok] (if (and ok (some? continue-work))
                                 (if (or (nil? token) (some? sleep))
                                   (as/take! (as/timeout (or sleep 10)) (run-observable-1! <chan >chan state continue-work))
                                   (run-observable-1! <chan >chan state continue-work))
                                 (unsub-fn)))]
          (put! >chan token cont-fn))
        (unsub-fn)))))

(ml/=> run-observable-1! [:function
                          [:=> [:cat type-chan type-chan [:map [::p/unsub-fn [:fn fn?]]] [:fn fn?]]
                           :any]])

(defn init-observable-1!
  [{{sub-fn ::p/sub-fn :as <observable} ::p/<observable :as config} impl-work]
  (let [{source-unsub-fn ::p/unsub-fn :keys [::p/<chan]} (sub-fn <observable)
        >chan (as/chan)
        unsub-fn (make-unsub-fn >chan source-unsub-fn)
        state (assoc config ::p/unsub-fn unsub-fn)]
    (run-observable-1! <chan >chan state impl-work)
    {::p/unsub-fn unsub-fn ::p/<chan >chan}))

(ml/=> init-observable-1! [:function
                           [:=> [:cat [:map [::p/<observable [:map [::p/sub-fn [:fn fn?]]]] [::p/sub-fn fn?]] [:fn fn?]]
                            [:map [::p/unsub-fn [:fn fn?]] [::p/<chan type-chan]]]])

(defn- run-observable-1-1-meta!
  [<chan >chan {:keys [::p/unsub-fn] :as state} impl-work]
  (as/take!
    <chan
    (fn [t]
      (if (some? t)
        (let [{:keys [::p/id]} (meta t)
              state (update state ::p/last-tokens #(assoc % id (with-meta t nil)))
              {:keys [::p/token ::p/state ::p/sleep ::p/continue-work]} (impl-work state t)
              cont-fn (fn [ok] (if (and ok (some? continue-work))
                                 (if (or (nil? token) (some? sleep))
                                   (as/take! (as/timeout (or sleep 10)) (run-observable-1-1-meta! <chan >chan state continue-work))
                                   (run-observable-1-1-meta! <chan >chan state continue-work))
                                 (unsub-fn)))]
          (put! >chan token cont-fn))
        (unsub-fn)))))

(ml/=> run-observable-1-1-meta! [:function
                                 [:=> [:cat type-chan type-chan [:map [::p/unsub-fn [:fn fn?]]] [:fn fn?]]
                                  :any]])

(defn- run-observable-x!
  [<chan >chan {:keys [::p/unsub-fn ::p/id ::p/last-token] :as state}]
  (as/take!
    <chan
    (fn [t]
      (cond
        (some? t) (let [state (assoc state ::p/last-token t)
                        t (with-meta t {::p/id id})]
                    ;(clojure.pprint/pprint ["----->1 " (::p/last-token state) t])
                    (as/put! >chan t (fn [ok] (if ok (run-observable-x! <chan >chan state) (unsub-fn)))))
        (not (token-complete? last-token)) (as/put! >chan (with-meta (token-complete) {::p/id id}) (fn [ok] (unsub-fn)))
        :else nil))))

(ml/=> run-observable-x! [:function
                          [:=> [:cat type-chan type-chan [:map [::p/unsub-fn [:fn fn?]]]]
                           :any]])

(defn init-observable-x!
  [{:keys [::p/<observables] :as config} impl-work]
  (let [<>chan (as/chan)
        >chan (as/chan)
        <observables (->> <observables
                          (normalize-map-coll)
                          (map (fn [[k {:keys [::p/sub-fn] :as <obs}]] [k (merge <obs (sub-fn <obs) {::p/id k})]))
                          (doall))
        unsub-fn (make-unsub-fn [<>chan >chan] (map (comp ::p/unsub-fn second) <observables))
        state (assoc config ::p/unsub-fn unsub-fn ::p/last-tokens (zipmap (map first <observables) (repeat nil)))]
    (run-observable-1-1-meta! <>chan >chan state impl-work)
    (doseq [[id {:keys [::p/<chan] :as <observable}] <observables]
      (run-observable-x! <chan <>chan <observable))
    {::p/unsub-fn unsub-fn ::p/<chan >chan}))

(ml/=> init-observable-x! [:function
                           [:=> [:cat [:map [::p/<observables [:or [:+ [:map [::p/sub-fn [:fn fn?]]]]
                                                               [:map-of :keyword [:map [::p/sub-fn [:fn fn?]]]]]]] [:fn fn?]]
                            [:map [::p/unsub-fn [:fn fn?]] [::p/<chan type-chan]]]])

(defn- run-observable-xs!
  [<chan-control <chan >chan {:keys [::p/<observables] :as state}]
  (let [[<chan {{:keys [::p/unsub-fn ::p/id]} ::p/<observable :as state}]
        (cond <chan [<chan state]
              (empty? <observables) [nil state]
              :else (let [[id {:keys [::p/sub-fn] :as <obs}] (first <observables)
                          {:keys [::p/unsub-fn ::p/<chan] :as <obs} (merge <obs (sub-fn <obs) {::p/id id})
                          mix (as/mix <chan)
                          <obs (assoc <obs ::p/unsub-fn (fn [] (do (as/unmix mix <chan-control) (unsub-fn))))]
                      (as/admix mix <chan-control)
                      [<chan (assoc state ::p/mix mix ::p/<observable <obs ::p/<observables (rest <observables))]))]
    (when <chan
      (as/take!
        <chan
        (fn [t]
          (cond
            (token-control? t) (unsub-fn)
            (and (token-complete? t) (> (count <observables) 1)) (do (unsub-fn) (run-observable-xs! <chan-control nil >chan state))
            (some? t) (let [t (with-meta t {::p/id id})]
                        ;(clojure.pprint/pprint ["----->1 " (::p/last-token state) t])
                        (as/put! >chan t (fn [ok] (if ok (run-observable-xs! <chan-control <chan >chan state) (unsub-fn)))))
            :else nil))))))

(ml/=> run-observable-xs! [:function
                           [:=> [:cat [:or type-chan :nil] type-chan
                                 [:map [::p/<observables [:sequential [:tuple number? [:map [::p/sub-fn [:fn fn?]]]]]]]]
                            :any]])

(defn init-observable-xs!
  [{:keys [::p/<observables] :as config} impl-work]
  (let [<>chan (as/chan)
        >chan (as/chan)
        {:keys [::p/unsub-control-fn ::p/>chan-control]} (make-unsub-control-fn)
        <observables (normalize-map-coll <observables)
        unsub-fn (make-unsub-fn [<>chan >chan] unsub-control-fn)
        state (assoc config ::p/unsub-fn unsub-fn ::p/<observables <observables
                            ::p/last-tokens (zipmap (map first <observables) (repeat nil)))]
    (run-observable-1-1-meta! <>chan >chan state impl-work)
    (run-observable-xs! >chan-control nil <>chan state)
    {::p/unsub-fn unsub-fn ::p/<chan >chan}))

(ml/=> init-observable-xs! [:function
                            [:=> [:cat [:map [::p/<observables [:+ [:map [::p/sub-fn [:fn fn?]]]]]] [:fn fn?]]
                             [:map [::p/unsub-fn [:fn fn?]] [::p/<chan type-chan]]]])

(defn- subscribe-work
  [<chan unsub-fn next-fn error-fn complete-fn]
  (as/take! <chan
               (fn [{:keys [::p/kind ::p/value ::p/error] :as m}]
                 (-> kind
                     (case
                       ::p/next (do (next-fn value) :cont)
                       ::p/error (do (error-fn error) :unsub)
                       ::p/complete (do (complete-fn) :noop)
                       :noop)
                     (case
                       :cont (subscribe-work <chan unsub-fn next-fn error-fn complete-fn)
                       :unsub (unsub-fn)
                       :noop nil)))))

(defn subscribe
  [{:keys [::p/next ::p/error ::p/complete] :as observer}
   {sub-fn ::p/sub-fn :as observable}]
  (let [next-fn (or next (when (fn? observer) observer) (constantly nil))
        error-fn (or error (constantly nil))
        complete-fn (or complete (constantly nil))
        {:keys [::p/<chan ::p/unsub-fn]} (sub-fn observable)]
    (subscribe-work <chan unsub-fn next-fn error-fn complete-fn)
    unsub-fn))

