(defproject lambdago "2020.08.26"
  :description "Software package for the Igo Math course https://egri-nagy.github.io/igomath/"
  :url "https://github.com/egri-nagy/lambdago"
  :license {:name "MIT License"
            :url "none"
            :year 2019
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.bhauman/rebel-readline "0.1.4"]
                 [instaparse "1.4.10"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [kigen "19.08.05"]
                 [metasoarous/oz "1.6.0-alpha6"]
                 [org.clojure/data.json "1.0.0"]
                 [trptcolin/versioneer "0.2.0"]]
  :plugins [[lein-cloverage "1.2.0"]
            [lein-kibit "0.1.8"]
            [lein-ancient "0.6.15"]
            [lein-bikeshed "0.5.2"]
            [jonase/eastwood "0.3.11"]]
  :main lgo.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :aliases {"rebl" ["trampoline" "run" "-m" "rebel-readline.main"]})
