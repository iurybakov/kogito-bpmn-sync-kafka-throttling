for n in {1..15}; do CURL="curl -d '{ \"restMeta\": { \"host\": \"string\", \"port\": 0, \"url\": \"string\", \"username\": \"string\", \"method\": \"string\" }, \"username\": \"user_${n}\"}' -H \"Content-Type: application/json\" -X POST http://localhost:8080/sequentialServiceTask?businessKey=key_${n}"; eval $CURL; done