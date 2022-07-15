# prolagus
Clojure/-Script asynchronous programming using clojure.async 

[https://en.wikipedia.org/wiki/Prolagus](https://en.wikipedia.org/wiki/Prolagus)

Example use:
```
(def unsub-fn (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                          ::m/error #(clojure.pprint/pprint ["xyz error" %])
                          ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                         (->> (interval 1500)
                              (take 4))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (->> (from [1 2 3 4 5]))))
                      
(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (->> (from [1 2 3 4 5])
                         (map inc))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (->> (from [1 2 3 4 5])
                         (buffer-count 2))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (->> (from [1 2 3 4 5 6 7 8])
                         (take 4))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (->> (interval 1500)
                         (take 5))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (->> (interval 1500)
                         (take 5)
                         (tap #(clojure.pprint/pprint ["xyz tap" %]))
                         (buffer-count 3 2))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (let [a (->> (interval 2500)
                                 (take 5)
                                 (map #(+ % 100)))
                          b (->> (interval 1500)
                                 (take 5)
                                 (map #(+ % 1000)))]
                      (merge {:a a :b b}))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (let [a (->> (interval 2500)
                                 (take 5)
                                 (map #(+ % 100)))
                          b (->> (interval 1500)
                                 (take 5)
                                 (map #(+ % 1000)))]
                      (merge [a b]))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (let [a (->> (interval 2500)
                                 (take 5)
                                 (map #(+ % 10)))
                          b (->> (interval 1500)
                                 (take 5)
                                 (map #(+ % 1000)))]
                      (combine-latest {:a a :b b}))))

(def xyz (subscribe {::m/next #(clojure.pprint/pprint ["xyz next" %])
                     ::m/error #(clojure.pprint/pprint ["xyz error" %])
                     ::m/complete #(clojure.pprint/pprint ["xyz complete"])}
                    (->> (from [1 2 3 4 5 6 7 8])
                         (filter #(> % 4)))))
```
