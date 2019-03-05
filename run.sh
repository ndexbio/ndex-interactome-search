#!/bin/bash

#rest service will be on the default port 8285
nohup java -Xmx1g -Dndex.host="http://localhost:8080/ndexbio-rest/v2" -jar interactomeSearch-0.1.0.jar & 1>out