# Send request to kafka ui for produce message in topic

for n in {1..70}; \
do \
  M_UUID=$(uuidgen); \
  M_DATE=$(date -u +"%Y-%m-%dT%H:%M:%S"); \
  R_URL='http://localhost:8081/api/clusters/local/topics/restmeta/messages'
  M_PAYLOAD=$(sed 's/"/\\"/g' <<-EOM
{
  "id": "${M_UUID}",
  "specversion": "0.3",
  "time": "${M_DATE}.000000+03:00",
  "type": "restmeta",
  "source": "restmeta",
  "data": {
    "host": "localhost",
    "port": 8000,
    "url": "string",
    "username": "string",
    "method": "POST",
    "await": "1000ms"
  }
}
EOM); \
  CURL="curl -d '{\"partition\":\"0\",\"key\":null,\"content\":\"${M_PAYLOAD}\"}' -H \"Content-Type: application/json\" -X POST ${R_URL}"; \
  eval $CURL; \
done