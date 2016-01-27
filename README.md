# ring-aws-lambda-adapter

A ring adapater for use with AWS Lambda and AWS API Gateway.

Develop locally using any ring server, and deploy to AWS Lambda and AWS API
Gateway.

This adapter is meant to be used with single lambda function behind multiple AWS
API Gateway routes, where routing of the request is done by a ring framework,
such as Compojure.

## Usage

See `example/` for a code example and Swagger definition.

1. Create a lambda function by passing a ring application to
   `ring-aws-lambda-adapter.core/defhandler`.

   For example:

   ```Clojure
   (ns example.lambda
     (:require [ring-aws-lambda-adapter.core :refer [defhandler]]
               [ring.middleware.params :refer [wrap-params]]
               [ring.middleware.keyword-params :refer [wrap-keyword-params]]
               [ring.middleware.json :refer [wrap-json-params
                                             wrap-json-response]]
               [ring.util.response :as r]
               [compojure.core :refer :all]))

   (defroutes app
     (GET "/" {params :params}
       (let [name (get params :name "World")]
         (-> (r/response {:message (format "Hello, %s" name)})))))

   (defhandler example.lambda.MyLambdaFn (wrap-json-response
                                          (wrap-json-params
                                           (wrap-params
                                            (wrap-keyword-params
                                             app))))
                                         {})
   ```

   ```Bash
   $ lein uberjar
   $ aws lambda create-function \
       --region eu-west-1 \
       --function-name my-lambda-project \
       --zip-file fileb://$(pwd)/target/my-lambda-project.jar \
       --role arn:aws:iam::YOUR-AWS-ACCOUNT-ID:role/lambda_basic_execution \
       --handler example.lambda.MyLambdaFn \
       --runtime java8 \
       --timeout 15 \
       --memory-size 512
   ```

   Note that the namespace that contains the `defhandler` must be AOT compiled.

2. Create an API Gateway endpoint with the following:
   - Integration Request
     - Mapping Templates
       - Content-Type: `application/json`

         ```Javascript
           {
             "stage" : "$context.stage",
             "request-id" : "$context.requestId",
             "api-id" : "$context.apiId",
             "resource-path" : "$context.resourcePath",
             "resource-id" : "$context.resourceId",
             "path" : "$input.params().path",
             "http-method" : "$context.httpMethod",
             "source-ip" : "$context.identity.sourceIp",
             "user-agent" : "$context.identity.userAgent",
             "account-id" : "$context.identity.accountId",
             "api-key" : "$context.identity.apiKey",
             "caller" : "$context.identity.caller",
             "user" : "$context.identity.user",
             "user-arn" : "$context.identity.userArn",
             "query-string": {
                #foreach($querystring in $input.params().querystring.keySet())
                  "$querystring": "$util.escapeJavaScript($input.params().querystring.get($querystring))" #if($foreach.hasNext),#end
                #end
             },
             "headers": {
                #foreach($header in $input.params().header.keySet())
                  "$header": "$util.escapeJavaScript($input.params().header.get($header))" #if($foreach.hasNext),#end
                #end
             },
             "body" : $input.json('$')
           }
         ```

   - Method Reponse
     - Create all your required status codes - at least 200 and 500; 200, 404 and 500 for this example.
   - Integration Response
     - Content-Type: `application/json`

       Lambda Error Regex | Method response status | Mapping template
       -------------------|------------------------|-----------------
                          | `200`                  | `$input.json('$.body')`
       `.*"status":404.*` | `404`                  | `{"error":"Not found"}`, or similar
       `.+`               | `500`                  | `{"error":"Unknown"}`, or similar

## Issues and Limitations

- Only works with JSON APIs
  - AWS API Gateway appears to be very JSON-centric anyway
- The response body or header values cannot be passed from the ring application,
  through API Gateway, to the end user for non-200 responses
  - This is due to an limitiation with the Lambda-API Gateway integration.
    Non-default response codes can only triggered using an exception. The
    exception body is treated as a string (even if it contains JSON), which does
    not allow for extracting the response body or header values in the
    "Integration Response"

## To Do

- Try importing Swagger with https://github.com/awslabs/aws-apigateway-importer
- Test URL path parameters, eg: `/path/{param1}/{param2}`

## License

Copyright © 2016 James Brennan

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
