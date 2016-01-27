(ns ring-aws-lambda-adapter.core-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [ring-aws-lambda-adapter.core :as core]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream File]))

(deftest handle-request-test
  (testing "response body types"
    (doseq [response-body ["foo"
                           (io/input-stream (.getBytes "foo"))
                           (list "foo")
                           (doto (File/createTempFile (str gensym)
                                                      ".tmp")
                             (.deleteOnExit)
                             (spit "foo"))]]
      (let [request (atom nil)
            options {}
            event {:headers
                   {:Via "1.1 example.com"
                    :X-Forwarded-Proto "http"
                    :X-Forwarded-Port "80"}
                   :source-ip "1.2.3.4"
                   :resource-path "/"
                   :query-string "?a=b"
                   :http-method "GET"
                   :body "foo"}
            in (io/input-stream (.getBytes (json/write-str event)))
            out (ByteArrayOutputStream.)
            ctx nil]
        (core/handle-request (fn [-request]
                               (reset! request -request)
                               {:status 200
                                :body response-body
                                :headers {}})
                             options
                             in
                             out
                             ctx)
        ;; Request
        (is (= (slurp (:body @request))
               "foo"))
        (is (= (dissoc @request :body)
               {:protocol           "HTTP/1.1",
                :remote-addr        "1.2.3.4",
                :headers            {"via"               "1.1 example.com"
                                     "x-forwarded-proto" "http"
                                     "x-forwarded-port"  "80"},
                :server-port        80,
                :event              event,
                :uri                "/",
                :server-name        "example.com",
                :ctx                nil,
                :query-string       "?a=b",
                :scheme             :http,
                :request-method     :get}))
        ;; Response
        (is (= (json/read-str (String. (.toByteArray out)))
               {"body" "foo"
                "status" 200
                "headers" {}}))))))
