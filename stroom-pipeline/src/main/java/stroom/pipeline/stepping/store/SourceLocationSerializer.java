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

import stroom.pipeline.shared.SourceLocation;
import stroom.util.json.JsonUtil;

import java.util.Arrays;

/**
 * Framing for a per-record {@link SourceLocation} snapshot in the store's shared-scope state slot.
 * <p>
 * A leading present-flag byte lets a record with no location snapshot round-trip as {@code null} while still
 * occupying a contiguous segment, so the per-part state file stays index-aligned with the record stream
 * ({@code segment == recordIndex - base}) the same way the element files are. The location itself is a small
 * structured value, so it is written as JSON - the same choice as the indicators in
 * {@link CapturedElementDataSerializer}.
 */
public final class SourceLocationSerializer {

    private static final byte ABSENT = 0;
    private static final byte PRESENT = 1;

    private SourceLocationSerializer() {
    }

    public static byte[] toBytes(final SourceLocation sourceLocation) {
        if (sourceLocation == null) {
            return new byte[]{ABSENT};
        }
        final byte[] json = JsonUtil.writeValueAsBytes(sourceLocation, false);
        final byte[] out = new byte[json.length + 1];
        out[0] = PRESENT;
        System.arraycopy(json, 0, out, 1, json.length);
        return out;
    }

    public static SourceLocation fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes[0] == ABSENT) {
            return null;
        }
        return JsonUtil.readValue(Arrays.copyOfRange(bytes, 1, bytes.length), SourceLocation.class);
    }
}
