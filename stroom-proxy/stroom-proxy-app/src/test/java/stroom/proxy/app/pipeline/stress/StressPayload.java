/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * A self-identifying, self-verifying file group.
 * <p>
 * Every payload carries its own identity and a checksum of its body, so the
 * terminal stage can answer two questions without any shared in-memory state:
 * <em>which</em> submission is this, and <em>is it intact</em>. Identity alone
 * would catch loss but not corruption, and a pipeline that moves bytes between
 * stores under injected faults can plausibly deliver a truncated or half-copied
 * group. Carrying the checksum in the data means a torn copy is caught at the
 * point of delivery rather than inferred from a count.
 * </p>
 * <p>
 * The file names are the real ones ({@code proxy.meta}, {@code proxy.zip},
 * {@code proxy.entries}) because the forward stage validates their presence.
 * </p>
 */
public final class StressPayload {

    static final String PAYLOAD_ID_HEADER = "StressPayloadId";
    static final String CHECKSUM_HEADER = "StressChecksum";

    private StressPayload() {
    }

    /**
     * Write a payload with the given id into an existing directory.
     *
     * @param dir The file-group directory.
     * @param payloadId The submission identity.
     * @param bodySize Size of the synthetic body in bytes.
     */
    public static void write(final Path dir,
                             final String payloadId,
                             final int bodySize) throws IOException {
        Files.createDirectories(dir);

        final byte[] body = body(payloadId, bodySize);

        Files.write(dir.resolve("proxy.zip"), body);
        Files.writeString(dir.resolve("proxy.entries"), "STRESS,RAW_EVENTS,1\n", StandardCharsets.UTF_8);
        Files.writeString(
                dir.resolve("proxy.meta"),
                "Feed:STRESS\n"
                + "Type:Raw Events\n"
                + PAYLOAD_ID_HEADER + ":" + payloadId + "\n"
                + CHECKSUM_HEADER + ":" + checksum(body) + "\n",
                StandardCharsets.UTF_8);
    }

    /**
     * Read a payload back and check it against its own checksum.
     *
     * @param dir The file-group directory.
     * @return What was found, including whether the body still matches.
     */
    public static Read read(final Path dir) throws IOException {
        final Path metaFile = dir.resolve("proxy.meta");
        if (!Files.isRegularFile(metaFile)) {
            return new Read(null, false, "no proxy.meta at " + dir);
        }

        String payloadId = null;
        String expectedChecksum = null;
        for (final String line : Files.readAllLines(metaFile, StandardCharsets.UTF_8)) {
            final int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            final String key = line.substring(0, colon);
            final String value = line.substring(colon + 1);
            if (PAYLOAD_ID_HEADER.equals(key)) {
                payloadId = value;
            } else if (CHECKSUM_HEADER.equals(key)) {
                expectedChecksum = value;
            }
        }

        if (payloadId == null) {
            return new Read(null, false, "proxy.meta at " + dir + " carries no " + PAYLOAD_ID_HEADER);
        }

        final Path bodyFile = dir.resolve("proxy.zip");
        if (!Files.isRegularFile(bodyFile)) {
            return new Read(payloadId, false, "no proxy.zip at " + dir);
        }
        if (!Files.isRegularFile(dir.resolve("proxy.entries"))) {
            return new Read(payloadId, false, "no proxy.entries at " + dir);
        }

        final String actualChecksum = checksum(Files.readAllBytes(bodyFile));
        if (!actualChecksum.equals(expectedChecksum)) {
            return new Read(
                    payloadId,
                    false,
                    "body checksum mismatch at " + dir
                    + ": expected " + expectedChecksum + " but was " + actualChecksum);
        }

        return new Read(payloadId, true, null);
    }

    /**
     * Deterministic body bytes for an id, so a re-read can be compared against a
     * freshly derived expectation as well as against the stored checksum.
     */
    private static byte[] body(final String payloadId, final int bodySize) {
        final byte[] seed = payloadId.getBytes(StandardCharsets.UTF_8);
        final byte[] body = new byte[Math.max(bodySize, 1)];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (seed[i % seed.length] + i);
        }
        return body;
    }

    private static String checksum(final byte[] bytes) {
        final CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return Long.toHexString(crc32.getValue());
    }

    /**
     * @param payloadId The identity read back, or null if it could not be read.
     * @param intact True if every expected file was present and the body matched
     * its checksum.
     * @param problem A description of what was wrong, or null if intact.
     */
    public record Read(String payloadId, boolean intact, String problem) {

    }
}
