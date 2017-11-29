# OpenTracing EJB Example
## How to run the example
### Start a Jaeger instance
See http://jaeger.readthedocs.io/en/latest/getting_started/
### To start the server
First, set the following EVs

+ export JAEGER_SERVICE_NAME=whatever-you-want
+ export JAEGER_REPORTER_LOG_SPANS=true 
+ export JAEGER_SAMPLER_TYPE=const
+ export JAEGER_SAMPLER_PARAM=1 

`mvn clean install wildfly-swarm:run`

### To validate
`curl -v -X POST localhost:8080/order`
Go to the Jaeger UI at localhost:16686 to verify that the traces were created.

### Run the intergration test
`mvn verify`