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

package stroom.data.store.impl.fs.s3v2;

import stroom.util.shared.NullSafe;

public enum ZstdSegmentationType {
    /**
     * The file has multiple parts. Each part is not further segmented.
     */
    PARTS,
    /**
     * The file is one single part, which is divided up into one or more segments.
     *
     */
    SEGMENTS,
    ;

    public static ZstdSegmentationType fromString(final String value) {
        if (NullSafe.isBlankString(value)) {
            return null;
        } else {
            if (value.equalsIgnoreCase(SEGMENTS.toString())) {
                return SEGMENTS;
            } else if (value.equalsIgnoreCase(PARTS.toString())) {
                return PARTS;
            } else {
                throw new IllegalArgumentException("Unrecognized segmentation type: " + value);
            }
        }
    }
}
