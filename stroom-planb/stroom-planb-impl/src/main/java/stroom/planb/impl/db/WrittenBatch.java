/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.planb.impl.db;

import stroom.meta.shared.Meta;

import java.nio.file.Path;
import java.util.List;

/**
 * Describes the set of locally-written LMDB parts produced by one pipeline
 * stream execution.
 *
 * <p>Each {@link WrittenPart} within the batch carries its own
 * {@link PartDestination}, so the publish step in {@link BatchDestination}
 * requires no routing logic.
 *
 * @param writerDir Root writer directory. Deleted by {@link BatchDestination#publish}
 *                  on success.
 * @param meta      The pipeline meta that produced this batch.
 * @param parts     All locally-written parts, each with its transfer destination.
 */
record WrittenBatch(
        Path writerDir,
        Meta meta,
        List<WrittenPart> parts
) {}
