#!/bin/bash
set -e

#IMPORTANT - This script requires HTTPie so please install it.

#Script to send an API request to stroom

requestFile=$1
path=$2

if [ "x" == "x${TOKEN}" ]; then
    echo "Error - TOKEN is not set, set it like 'export TOKEN=\".....\"' where ..... is the JWT token from 'Tools->API' Keys in stroom" 
    exit 1
fi

if [ "x" == "x${requestFile}" ] || [ "x" == "x${path}" ]; then
    echo "Error - Usage: ./sendReq.sh file path"
    echo "          e.g: ./sendReq.sh ~/req.json /api/sqlstatistics/v2/search"
    echo "          e.g: ./sendReq.sh ~/req.json /api/stroom-index/v2/search"
    exit 1
fi

cat $requestFile | http POST http://localhost:8080${path} "Authorization:Bearer ${TOKEN}" 
