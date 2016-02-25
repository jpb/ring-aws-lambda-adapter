(defproject ring-aws-lambda-adapter "0.1.1"
  :description "A ring adapater for use with AWS Lambda and AWS API Gateway"
  :url "https://github.com/jpb/ring-aws-lambda-adapter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.json "0.2.6"]
                 [ring/ring-codec "1.0.0"]
                 [uswitch/lambada "0.1.0"]])
