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

package stroom.planb.impl.fs;

import java.nio.file.Path;

/**
 * A locally-produced archive shard ready to be pushed to the shared store.
 *
 * @param dateLabel  directory name under the shard-index dir on the shared store
 *                   (e.g. "2025-05-18" for DAY granularity)
 * @param localDir   local temporary directory containing data.mdb
 */
public record StagedArchive(String dateLabel, Path localDir) {
}
