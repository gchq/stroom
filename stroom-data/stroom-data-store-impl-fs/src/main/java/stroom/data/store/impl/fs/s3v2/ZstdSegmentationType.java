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
import stroom.util.shared.string.CIKey;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ZstdSegmentationType {
    /**
     * The file has multiple parts. Each part is not further segmented.
     */
    PARTS,
    /**
     * The file is one single part, which is divided up into multiple segments.
     *
     */
    SEGMENTS,
    /**
     * The file is neither multipart nor multi-segment. It is one single Zstd frame.
     */
    NONE,
    ;

    private static final Map<CIKey, ZstdSegmentationType> FROM_STRING_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(
                    type ->
                            CIKey.internStaticKey(type.toString()),
                    Function.identity()));

    public static ZstdSegmentationType fromString(final String value) {
        if (NullSafe.isBlankString(value)) {
            throw new IllegalArgumentException("Value must not be blank");
        } else {
            final ZstdSegmentationType zstdSegmentationType = FROM_STRING_MAP.get(CIKey.of(value));
            if (zstdSegmentationType == null) {
                throw new IllegalArgumentException("Unrecognized segmentation type: " + value);
            }
            return zstdSegmentationType;
        }
    }
}
