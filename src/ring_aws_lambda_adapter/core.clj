(ns ring-aws-lambda-adapter.core
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [ring.util.codec :as codec]
            [uswitch.lambada.core :refer [deflambdafn]])
  (:import [java.io InputStream File]
           [clojure.lang ISeq]))

(defn interpolate-path [params path]
  (reduce (fn -interpolate-path [acc [key value]]
            (str/replace acc
                         (re-pattern (format "\\{%s\\}" (name key)))
                         value))
          path
          params))

(defn event->request
  "Transform lambda input to Ring requests. Has two extra properties:
   :event - the lambda input
   :context - an instance of a lambda context
              http://docs.aws.amazon.com/lambda/latest/dg/java-context-object.html"
  [event context]
  (let [[http-version host]
        (str/split (get-in event [:headers :Via] "") #" ")]
    {:server-port
     (try
       (Integer/parseInt (get-in event [:headers :X-Forwarded-Port]))
       (catch NumberFormatException e nil))
     :body           (-> (get event :body {})
                         json/write-str
                         (.getBytes)
                         io/input-stream)
     :server-name    host
     :remote-addr    (get event :source-ip "")
     :uri            (interpolate-path (get event :path {})
                                       (get event :resource-path ""))
     :query-string   (codec/form-encode (get event :query-string {}))
     :scheme         (keyword
                      (get-in event [:headers :X-Forwarded-Proto]))
     :request-method (keyword
                      (str/lower-case (get event :http-method "")))
     :protocol       (format "HTTP/%s" http-version)
     :headers        (into {} (map (fn -header-keys [[k v]]
                                     [(str/lower-case (name k)) v])
                                   (:headers event)))
     :event          event
     :context        context}))

(defn proxy-event->request
  "Transform lambda input of an API Proxy request to Ring requests.
   Has two extra properties:
   :event - the lambda input
   :context - an instance of a lambda context
              http://docs.aws.amazon.com/lambda/latest/dg/java-context-object.html"
  [event context]
  (let [[http-version host]
        (str/split (get-in event [:headers :Via] "") #" ")]
    {:server-port
     (try
       (Integer/parseInt (get-in event [:headers :X-Forwarded-Port]))
       (catch NumberFormatException e nil))
     :body           (get event :body)
     :server-name    host
     :uri            (get event :path "/")
     :query-params   (get event :queryStringParameters {})
     :scheme         (keyword
                      (get-in event [:headers :X-Forwarded-Proto]))
     :request-method (keyword
                      (str/lower-case (get event :httpMethod "")))
     :protocol       (format "HTTP/%s" http-version)
     :headers        (into {} (map (fn -header-keys [[k v]]
                                     [(str/lower-case (name k)) v])
                                   (:headers event)))
     :event          event
     :context        context}))

(defprotocol ResponseBody
  (wrap-body [body]))

(extend-protocol ResponseBody
  InputStream
  (wrap-body [body] (slurp body))
  File
  (wrap-body [body] (slurp body))
  ISeq
  (wrap-body [body] (str/join "\n" (map wrap-body body)))
  nil
  (wrap-body [body] body)
  Object
  (wrap-body [body] body))

(defn maybe-decode-json [json]
  (try
    (json/read-str json)
       (catch Exception e
         json)))

(defn maybe-encode-json [json]
  (try
    (json/write-str json)
      (catch Exception e
        json)))

(defn wrap-response [response]
  (if (map? response)
    (update-in response
               [:body]
               (comp maybe-decode-json wrap-body))
    response))

(defn handle-request
  "Handle a lambda invocation as a ring request. Writes ring response as JSON to
  `out` for 200 responses, raises an exception with ring response as JSON for
  non-200 responses"
  [handler options in out context]
  (let [event (json/read (io/reader in)
                          :key-fn keyword)
        request (event->request event context)
        response (-> request
                     handler
                     wrap-response)]
    (if (= (:status response) 200)
      (with-open [w (io/writer out)]
        (json/write response w))
      (throw (Exception. (json/write-str response))))))

(defn handle-proxy-request
  "Handle a lambda invocation as a ring request. Writes ring response as JSON to
  `out` for 200 responses, raises an exception with ring response as JSON for
  non-200 responses"
  [handler options in out context]
  (let [event (json/read (io/reader in)
                         :key-fn keyword)
        request (proxy-event->request event context)
        response (-> request
                     handler
                     wrap-response)
        status (:status response)
        body   (:body response)
        formatted (-> response
                      (assoc :statusCode status)
                      (assoc :body (maybe-encode-json body))
                      (select-keys [:statusCode :headers :body]))]
    (with-open [w (io/writer out)]
      (json/write formatted w))))

(defmacro defhandler
  "A ring handler for AWS Lambda and AWS API Gateway"
  [name handler {:keys [api-proxy] :as options}]
  `(deflambdafn ~name
     [in# out# context#]
     (if ~api-proxy
       (handle-proxy-request ~handler ~options in# out# context#)
       (handle-request ~handler ~options in# out# context#))))
