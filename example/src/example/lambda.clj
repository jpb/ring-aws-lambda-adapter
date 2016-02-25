(ns example.lambda
  (:require [ring-aws-lambda-adapter.core :refer [defhandler]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-params
                                          wrap-json-response]]
            [ring.util.response :as r]
            [compojure.core :refer :all]
            [compojure.route :as route]))

(defroutes app-routes
  (GET "/" {params :params}
    (let [name (get params :name "World")]
      (-> (r/response {:message (format "Hello, %s" name)})
          (r/header "Name" name))))

  (POST "/" {params :params}
    (let [name (get params :name "World")]
      (-> (r/response {:message (format "Hello, %s" name)})
          (r/header "Name" name)
          (r/status 204))))

  (GET "/boom" {params :params}
    (throw (Exception. "boom!")))

  (PUT "/a/:b/:c/:d" {uri :uri}
    (r/response {:uri uri}))

  (GET "/a/:b/:c/:d" {uri :uri}
    (r/response {:uri uri}))

  (route/not-found "{\"error\":\"Not found\"}"))

(def app
  (wrap-json-response
   (wrap-json-params
    (wrap-params
     (wrap-keyword-params
      app-routes)))))

(defhandler example.lambda.MyLambdaFn app {})
