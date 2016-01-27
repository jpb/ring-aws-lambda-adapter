# Ring AWS Lambda API Gateway Example

## Development

```
lein ring server
```

```
curl -v localhost:3000\?name=Local
curl -v -H 'Content-Type: application/json' \
     -d '{"name":"Local"}' \
     localhost:3000
curl -v localhost:3000/not-found
curl -v localhost:3000/boom > /dev/null
```

## API Gateway

```
URL=https://abc123.execute-api.region.amazonaws.com/stage
curl -v $URL\?name=Local
curl -v -H 'Content-Type: application/json' \
     -d '{"name":"Local"}' \
     $URL
curl -v $URL/not-found
curl -v $URL/boom
```
