# prolagus
Clojure/-Script asynchronous programming using clojure.async 

[https://en.wikipedia.org/wiki/Prolagus](https://en.wikipedia.org/wiki/Prolagus)

Example use:
```
(require '[prolagus.system :as ps])
(require '[prolagus.operator :as po])
(require '[prolagus :as p])

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (->> (po/interval 1500)
                                 (po/take 4))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (->> (po/from [1 2 3 4 5]))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (->> (po/from [1 2 3 4 5])
                                 (po/map inc))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (->> (po/from [1 2 3 4 5])
                                 (po/buffer-count 2))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (->> (po/from [1 2 3 4 5 6 7 8])
                                 (po/take 4))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (->> (po/interval 1500)
                                 (po/take 5)
                                 (po/tap #(clojure.pprint/pprint ["xyz tap" %]))
                                 (po/buffer-count 3 2))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (let [a (->> (po/interval 2500)
                                         (po/take 5)
                                         (po/map #(+ % 100)))
                                  b (->> (po/interval 1500)
                                         (po/take 5)
                                         (po/map #(+ % 1000)))]
                              (po/merge {:a a :b b}))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (let [a (->> (po/interval 2500)
                                         (po/take 5)
                                         (po/map #(+ % 100)))
                                  b (->> (po/interval 1500)
                                         (po/take 5)
                                         (po/map #(+ % 1000)))]
                              (po/merge [a b]))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (let [a (->> (po/interval 2500)
                                         (po/take 5)
                                         (po/map #(+ % 10)))
                                  b (->> (po/interval 1500)
                                         (po/take 5)
                                         (po/map #(+ % 1000)))]
                              (po/combine-latest {:a a :b b}))))

(def xyz (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                        ::p/error #(clojure.pprint/pprint ["xyz error" %])
                        ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                       (->> (po/from [1 2 3 4 5 6 7 8])
                            (po/filter #(> % 4)))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (let [a (->> (po/interval 2500)
                                         (po/take 5)
                                         (po/map #(+ % 10)))
                                  b (->> (po/interval 1500)
                                         (po/take 5)
                                         (po/map #(+ % 1000)))]
                              (po/concat [a b]))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (let [a (->> (po/from [1]))
                                  b (->> (po/from [5]))]
                              (po/concat [a]))))

(def unsub-fn (ps/subscribe {::p/next #(clojure.pprint/pprint ["xyz next" %])
                             ::p/error #(clojure.pprint/pprint ["xyz error" %])
                             ::p/complete #(clojure.pprint/pprint ["xyz complete"])}
                            (->> (po/from [1 2 3 4 5 6 7 8])
                                 (po/map inc)
                                 (po/start-with :a))))                              
```
