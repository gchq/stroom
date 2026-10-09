#!/usr/bin/env bash
#
# Copyright 2026 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Walks shards one after another (a "lane") against the live Stroom, each into out/<round>-<name>,
# and logs when each finishes to out/<round>-lanes.log. Run at most TWO lanes at once: each walker's
# Chrome grows with what it walks, and three or more have run this machine out of memory.
#
#   ./lane.sh <round> "<name>:<ENV=value ...>" ...
#
# e.g. a lane of menu areas and a lane of document types:
#
#   ./lane.sh r8 "nav:AREA=Navigation" "sec:AREA=Security"
#   ./lane.sh r8 "docs-a:RECIPES=oracles/route-recipes.json RECIPEKIND=doc DOCTYPES=Feed,Dictionary"
#
# Then merge the round into the evidence and rebuild the ledger with ./merge.sh <round>.
# STROOM_USER and STROOM_PASS default to the local development account (admin / a).

set -euo pipefail

SUITE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [[ $# -lt 2 ]]; then
  sed -n '17,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
fi

round=$1
shift
for item in "$@"; do
  name=${item%%:*}
  envs=${item#*:}
  out="${SUITE}/out/${round}-${name}"
  rm -rf "${out}"
  mkdir -p "${out}"
  echo "lane: ${name} started $(date +%F' '%H:%M)" >> "${SUITE}/out/${round}-lanes.log"
  # shellcheck disable=SC2086 # the shard's settings are deliberately split into words
  env STROOM_USER="${STROOM_USER:-admin}" STROOM_PASS="${STROOM_PASS:-a}" ${envs} \
    MAXNODES=5000 POSTDEPTH=1 OUT="${out}" \
    node "${SUITE}/walk.mjs" > "${out}/walk.log" 2>&1 < /dev/null || true
  echo "lane: ${name} done $(date +%F' '%H:%M)" >> "${SUITE}/out/${round}-lanes.log"
done
