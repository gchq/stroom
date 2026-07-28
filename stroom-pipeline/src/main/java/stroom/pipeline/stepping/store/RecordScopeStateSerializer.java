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

import stroom.util.logging.LogUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Framing for one record's {@link RecordScopeState} in the store's per-part state slot.
 * <p>
 * A record always occupies a segment, even with nothing to say, so the state file stays index-aligned with
 * the record stream ({@code segment == recordIndex - base}) exactly as the element files are. The source
 * location is delegated to {@link SourceLocationSerializer} (length-prefixed so it can be skipped over); the
 * {@code stroom:put} map is written as length-prefixed UTF-8 pairs rather than JSON because a put value is
 * arbitrary user data of unbounded length and may be null, neither of which
 * {@link DataOutputStream#writeUTF} handles.
 */
public final class RecordScopeStateSerializer {

    private static final int NULL_LENGTH = -1;
    private static final int ABSENT_MAP = -1;

    private RecordScopeStateSerializer() {
    }

    public static byte[] toBytes(final RecordScopeState state) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (final DataOutputStream output = new DataOutputStream(out)) {
            final byte[] locationBytes = SourceLocationSerializer.toBytes(
                    state == null ? null : state.sourceLocation());
            output.writeInt(locationBytes.length);
            output.write(locationBytes);

            final Map<String, String> scopeMap = state == null ? null : state.scopeMap();
            if (scopeMap == null) {
                output.writeInt(ABSENT_MAP);
            } else {
                output.writeInt(scopeMap.size());
                for (final Map.Entry<String, String> entry : scopeMap.entrySet()) {
                    writeString(output, entry.getKey());
                    writeString(output, entry.getValue());
                }
            }
        } catch (final IOException e) {
            throw new StepDataStoreException(LogUtil.message("Unable to serialise record scope state: {}",
                    e.getMessage()), e);
        }
        return out.toByteArray();
    }

    public static RecordScopeState fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (final DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            final int locationLength = input.readInt();
            final byte[] locationBytes = new byte[locationLength];
            input.readFully(locationBytes);

            final int size = input.readInt();
            Map<String, String> scopeMap = null;
            if (size != ABSENT_MAP) {
                scopeMap = new HashMap<>(Math.max(1, size));
                for (int i = 0; i < size; i++) {
                    final String key = readString(input);
                    scopeMap.put(key, readString(input));
                }
            }
            return new RecordScopeState(SourceLocationSerializer.fromBytes(locationBytes), scopeMap);
        } catch (final IOException e) {
            throw new StepDataStoreException(LogUtil.message("Unable to deserialise record scope state: {}",
                    e.getMessage()), e);
        }
    }

    private static void writeString(final DataOutputStream output, final String value) throws IOException {
        if (value == null) {
            output.writeInt(NULL_LENGTH);
        } else {
            final byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            output.writeInt(utf8.length);
            output.write(utf8);
        }
    }

    private static String readString(final DataInputStream input) throws IOException {
        final int length = input.readInt();
        if (length == NULL_LENGTH) {
            return null;
        }
        final byte[] utf8 = new byte[length];
        input.readFully(utf8);
        return new String(utf8, StandardCharsets.UTF_8);
    }
}
