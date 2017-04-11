#!/usr/bin/env bash
#
# Licensed to Big Data Genomics (BDG) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The BDG licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e
if [ -z "${SPARK_HOME}" ]; then
  export SPARK_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

. "${SPARK_HOME}"/bin/load-spark-env.sh

# Find the java binary
if [ -n "${JAVA_HOME}" ]; then
  RUNNER="${JAVA_HOME}/bin/java"
else
  if [ `command -v java` ]; then
    RUNNER="java"
  else
    echo "JAVA_HOME is not set" >&2
    exit 1
  fi
fi

# Find assembly jar
SPARK_ASSEMBLY_JAR=
if [ -f "${SPARK_HOME}/RELEASE" ]; then
  ASSEMBLY_DIR="${SPARK_HOME}/lib"
else
  ASSEMBLY_DIR="${SPARK_HOME}/assembly/target/scala-$SPARK_SCALA_VERSION"
fi

GREP_OPTIONS=
num_jars="$(ls -1 "$ASSEMBLY_DIR" | grep "^spark-assembly.*hadoop.*\.jar$" | wc -l)"
if [ "$num_jars" -eq "0" -a -z "$SPARK_ASSEMBLY_JAR" -a "$SPARK_PREPEND_CLASSES" != "1" ]; then
  echo "Failed to find Spark assembly in $ASSEMBLY_DIR." 1>&2
  echo "You need to build Spark before running this program." 1>&2
  exit 1
fi
if [ -d "$ASSEMBLY_DIR" ]; then
  ASSEMBLY_JARS="$(ls -1 "$ASSEMBLY_DIR" | grep "^spark-assembly.*hadoop.*\.jar$" || true)"
  if [ "$num_jars" -gt "1" ]; then
    echo "Found multiple Spark assembly jars in $ASSEMBLY_DIR:" 1>&2
    echo "$ASSEMBLY_JARS" 1>&2
    echo "Please remove all but one jar." 1>&2
    exit 1
  fi
fi

echo "${ASSEMBLY_DIR}/${ASSEMBLY_JARS}"
