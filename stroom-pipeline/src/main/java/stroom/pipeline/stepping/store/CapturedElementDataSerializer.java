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

import stroom.pipeline.stepping.store.CapturedData.Format;
import stroom.util.json.JsonUtil;
import stroom.util.shared.Indicators;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Binary framing for a {@link CapturedElementData} record in the store: three flags, the indicators (as
 * JSON, since they are small and structured), and each IO side as a tagged, length-prefixed blob - the SAX
 * event bytes or the text bytes are written straight through, not JSON-escaped or base64'd.
 */
public final class CapturedElementDataSerializer {

    // Nullable-CapturedData markers.
    private static final byte ABSENT = 0;
    private static final byte SAX_EVENTS = 1;
    private static final byte TEXT = 2;

    private CapturedElementDataSerializer() {
    }

    public static byte[] toBytes(final CapturedElementData data) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (final DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBoolean(data.formatInput());
            out.writeBoolean(data.formatOutput());
            out.writeBoolean(data.hasOutput());
            writeBlock(out, data.indicators() == null
                    ? null
                    : JsonUtil.writeValueAsBytes(data.indicators(), false));
            writeCapturedData(out, data.input());
            writeCapturedData(out, data.output());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static CapturedElementData fromBytes(final byte[] bytes) {
        try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            final boolean formatInput = in.readBoolean();
            final boolean formatOutput = in.readBoolean();
            final boolean hasOutput = in.readBoolean();
            final byte[] indicatorBytes = readBlock(in);
            final Indicators indicators = indicatorBytes == null
                    ? null
                    : JsonUtil.readValue(indicatorBytes, Indicators.class);
            final CapturedData input = readCapturedData(in);
            final CapturedData output = readCapturedData(in);
            return new CapturedElementData(input, output, formatInput, formatOutput, hasOutput, indicators);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeCapturedData(final DataOutputStream out, final CapturedData data)
            throws IOException {
        if (data == null) {
            out.writeByte(ABSENT);
            return;
        }
        out.writeByte(data.format() == Format.SAX_EVENTS ? SAX_EVENTS : TEXT);
        writeBlock(out, data.data());
    }

    private static CapturedData readCapturedData(final DataInputStream in) throws IOException {
        final byte tag = in.readByte();
        return switch (tag) {
            case ABSENT -> null;
            case SAX_EVENTS -> new CapturedData(Format.SAX_EVENTS, readBlock(in));
            case TEXT -> new CapturedData(Format.TEXT, readBlock(in));
            default -> throw new IOException("Unknown captured-data tag: " + tag);
        };
    }

    private static void writeBlock(final DataOutputStream out, final byte[] block) throws IOException {
        if (block == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(block.length);
        out.write(block);
    }

    private static byte[] readBlock(final DataInputStream in) throws IOException {
        final int len = in.readInt();
        if (len < 0) {
            return null;
        }
        final byte[] block = new byte[len];
        in.readFully(block);
        return block;
    }
}
