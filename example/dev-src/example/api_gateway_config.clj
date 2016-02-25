(ns example.api-gateway-config
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [org.raml.parser.visitor RamlDocumentBuilder]))

(defn raml [location]
  (.build (RamlDocumentBuilder.) location))

(defn routes
  ([raml] (routes raml {} ""))
  ([raml acc-routes parent-path]
   (let [resources (.getResources raml)]
     (reduce (fn [acc path]
               (let [resource (get resources path)
                     route (str parent-path path)]
                 (routes resource
                         (assoc acc route (into {}
                                                (map (fn [[method action]]
                                                       [(str/lower-case (str method))
                                                        (keys (.getResponses action))])
                                                     (.getActions resource))))
                         route)))
             acc-routes
             (keys resources)))))

(defn default-status-code [status-codes]
  (or (first (filter (fn [code] (< 199 (Integer/parseInt code) 300))
                     status-codes))
      "200"))

(defn format-uri [template account-id region name]
  (format template region account-id name))

(defn api-gateway-config [routes route-config]
  (reduce (fn -api-gateway-config [acc [route methods]]
            (if (empty? methods)
              acc
              (assoc acc
                     route
                     (into {}
                           (map (fn [[method status-codes]]
                                  [method (assoc-in route-config
                                                    ["integration"
                                                     "responses"
                                                     "default"
                                                     "statusCode"]
                                                    (default-status-code status-codes))])
                                methods)))))
          {}
          routes))

(defn -main [account-id region name & args]
  (let [route-config (-> "aws-route.json"
                         io/resource
                         io/reader
                         json/parse-stream
                         (update-in ["integration" "uri"] format-uri account-id region name))]
    (-> "example.raml"
        raml
        routes
        (api-gateway-config route-config)
        json/encode
        println)))
