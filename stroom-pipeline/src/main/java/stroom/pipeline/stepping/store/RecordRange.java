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

package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.stepping.StepLocation;

/**
 * A contiguous run of records within one part: the records a step is about. The capture side materialises
 * one on demand (a single record is the edit-then-refresh case; a longer run is a prefetch window or a
 * window scanned for a filter match), and the read side plans against one (a demand the feed's coverage
 * must hold). It lives here, beside {@link Coverage}, because both sides of the capture/read boundary speak
 * it and the store is where they meet.
 */
public record RecordRange(long partIndex, long firstRecord, long lastRecord) {

    public static RecordRange of(final StepLocation location) {
        return new RecordRange(location.getPartIndex(), location.getRecordIndex(),
                location.getRecordIndex());
    }
}
