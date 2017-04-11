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

# Figure out where MANGO is installed
SCRIPT_DIR="$(cd `dirname $0`/..; pwd)"

# Setup CLASSPATH like appassembler

# Assume we're running in a binary distro
MANGO_CMD="$SCRIPT_DIR/bin/mango"
REPO="$SCRIPT_DIR/repo"

# Fallback to source repo
if [ ! -f $MANGO_CMD ]; then
MANGO_CMD="$SCRIPT_DIR/mango-play/target/appassembler/bin/mango"
REPO="$SCRIPT_DIR/mango-play/target/appassembler/repo"
fi

if [ ! -f "$MANGO_CMD" ]; then
  echo "Failed to find appassembler scripts in $BASEDIR/bin"
  echo "You need to build MANGO before running this program"
  exit 1
fi
eval $(cat "$MANGO_CMD" | grep "^CLASSPATH")

echo "$CLASSPATH"
