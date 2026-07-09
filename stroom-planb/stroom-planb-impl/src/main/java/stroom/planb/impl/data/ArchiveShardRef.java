/*
 * Copyright 2016-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.planb.impl.data;

import stroom.planb.shared.ArchivalGranularity;

import java.nio.file.Path;

/**
 * Identifies a single archive shard directory that is complete and
 * time-range eligible for a given query.
 *
 * @param dateLabel   directory name under the per-shard archive dir
 *                    (e.g. {@code "2026-07-06"} for DAY granularity)
 * @param dir         absolute path to the archive shard directory on the shared store
 * @param granularity the time bucket granularity detected from the date label
 */
public record ArchiveShardRef(String dateLabel, Path dir, ArchivalGranularity granularity) {

}
