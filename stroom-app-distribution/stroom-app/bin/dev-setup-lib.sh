#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

. ${DIR}/common.sh

cd ${DIR}/..

rm -fr lib/*
mvn dependency:copy-dependencies -DoutputDirectory=lib -DincludeGroupIds=stroom,mysql

