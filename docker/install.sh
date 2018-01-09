#!/bin/bash

set -e

SAM_DIR=$1
cd $SAM_DIR

# Tests are run in jenkins which has a custom opendj instance just for testing
sbt -J-Xms2g -J-Xmx2g test -Ddirectory.url=$DIRECTORY_URL -Ddirectory.password=$DIRECTORY_PASSWORD
sbt -J-Xms2g -J-Xmx2g assembly
SAM_JAR=$(find target | grep 'sam.*\.jar')
mv $SAM_JAR .
sbt clean
