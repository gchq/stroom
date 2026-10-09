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

package stroom.planb.impl.serde;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.dao.session.SessionDb;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.keyprefix.KeyPrefixSerdeFactory;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.impl.serde.temporalkey.TerminatedStringKeySerde;
import stroom.planb.impl.serde.time.MillisecondTimeSerde;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SessionKeySchema;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.StateType;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The encoding behind the FloorMap Event Store's per-key seek.
 *
 * <p>The property under test is <b>prefix-freeness</b>: no key's encoded bytes may be a prefix of
 * another's. A reader that steps past a key by jumping beyond {@code prefix + 0xFF...} relies on it.
 * Without it {@code door10} is jumped over, because {@code door1} prefixes it, and that entity
 * silently never appears on the map.</p>
 */
class TestTerminatedStringKeySerde {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00.000Z");

    private static TerminatedStringKeySerde serde() {
        return new TerminatedStringKeySerde(BYTE_BUFFERS, new MillisecondTimeSerde());
    }

    /** The encoded bytes of a key, copied to the heap so they outlive the serde's buffer. */
    private static byte[] encode(final String key) {
        final byte[][] holder = new byte[1][];
        serde().write(null, temporalKey(key), buffer -> {
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.duplicate().get(bytes);
            holder[0] = bytes;
        });
        return holder[0];
    }

    private static TemporalKey temporalKey(final String key) {
        return TemporalKey.builder().prefix(KeyPrefix.create(key)).time(TIME).build();
    }

    private static boolean isPrefixOf(final byte[] shorter, final byte[] longer) {
        if (shorter.length > longer.length) {
            return false;
        }
        for (int i = 0; i < shorter.length; i++) {
            if (shorter[i] != longer[i]) {
                return false;
            }
        }
        return true;
    }

    /** Unsigned comparison, matching LMDB's default key comparator. */
    private static int compareUnsigned(final byte[] a, final byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            final int diff = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (diff != 0) {
                return diff;
            }
        }
        return a.length - b.length;
    }

    @Test
    void keyThatExtendsAnotherIsNotEncodedAsItsPrefix() {
        // The exact case that defeats a seek over an unterminated encoding.
        final byte[] door1 = encode("door1");
        final byte[] door10 = encode("door10");

        assertThat(isPrefixOf(door1, door10))
                .as("door1's bytes must not prefix door10's, or a seek past door1 skips door10")
                .isFalse();
    }

    @Test
    void noKeyInARealisticSetPrefixesAnother() {
        final String[] keys = {"a", "ab", "abc", "door1", "door10", "door11", "door2", "door"};
        for (final String outer : keys) {
            for (final String inner : keys) {
                if (!outer.equals(inner)) {
                    assertThat(isPrefixOf(encode(outer), encode(inner)))
                            .as("%s must not prefix %s", outer, inner)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void orderingMatchesTheRawStrings() {
        // The advantage over a length prefix, which would sort length-major: "z" before "aa".
        assertThat(compareUnsigned(encode("door1"), encode("door10"))).isNegative();
        assertThat(compareUnsigned(encode("door10"), encode("door2"))).isNegative();
        assertThat(compareUnsigned(encode("a"), encode("ab"))).isNegative();
        assertThat(compareUnsigned(encode("z"), encode("aa"))).isPositive();
    }

    @Test
    void roundTripsKeyAndTime() {
        serde().write(null, temporalKey("door10"), buffer -> {
            final TemporalKey read = serde().read(null, buffer.duplicate());
            assertThat(read.getPrefix().getVal().toString()).isEqualTo("door10");
            assertThat(read.getTime()).isEqualTo(TIME);
        });
    }

    @Test
    void roundTripsAKeyThatIsEmpty() {
        serde().write(null, temporalKey(""), buffer -> {
            final TemporalKey read = serde().read(null, buffer.duplicate());
            assertThat(read.getPrefix().getVal().toString()).isEmpty();
            assertThat(read.getTime()).isEqualTo(TIME);
        });
    }

    @Test
    void anEmbeddedNullIsRejectedRatherThanCorruptingTheOrdering() {
        final String withNull = "do" + (char) 0 + "or";
        assertThatThrownBy(() -> serde().write(null, temporalKey(withNull), buffer -> {
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null byte");
    }

    @Test
    void theTerminatorSitsBetweenTheKeyAndTheTime() {
        final byte[] encoded = encode("ab");
        final int timeSize = new MillisecondTimeSerde().getSize();

        assertThat(encoded).hasSize(2 + 1 + timeSize);
        assertThat(encoded[2]).isEqualTo((byte) 0x00);
    }

    /**
     * The encoding is offered only where it buys something.
     *
     * <p>Prefix-freeness matters to a reader that seeks per key, which is the temporal state store's
     * snapshot read. A store that scans gains nothing from it, so rather than implement the encoding
     * three more times the other factories refuse it — and it is absent from
     * {@link KeyType#ORDERED_LIST}, so no user can select it there in the first place.</p>
     */
    @Test
    void theEncodingIsRefusedWhereNothingWouldSeek() {
        assertThatThrownBy(() -> KeyPrefixSerdeFactory.createKeySerde(
                KeyType.TERMINATED_STRING, null, null, BYTE_BUFFERS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temporal state");
    }

    /**
     * A session store refuses the encoding too, and refuses it while opening rather than later.
     *
     * <p>Exercised through a real store rather than a mock: {@code SessionDb.create} builds the key
     * serde from the document's own key schema, so the document <em>is</em> the input, and mocking
     * anything here would only have tested the mock. It also shows the refusal arrives at open time
     * rather than at first use. (It does not assert that the environment was closed behind it;
     * {@code create} does close it, but nothing here observes that.)</p>
     */
    @Test
    void sessionStoreRefusesTheEncodingWhenItOpens(@TempDir final Path tempDir) {
        final PlanBDoc sessionDoc = PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("sessions")
                .stateType(StateType.SESSION)
                .settings(new SessionSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .keySchema(new SessionKeySchema.Builder()
                                .keyType(KeyType.TERMINATED_STRING)
                                .build())
                        .build())
                .build();

        assertThatThrownBy(() -> SessionDb.create(tempDir, BYTE_BUFFERS, sessionDoc, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temporal state");
    }

    @Test
    void theEncodingIsNotOfferedInTheSettingsDropdown() {
        assertThat(KeyType.ORDERED_LIST)
                .as("ORDERED_LIST feeds every Plan B key-type dropdown")
                .doesNotContain(KeyType.TERMINATED_STRING);
    }

    @Test
    void anOverlongKeyIsRejected() {
        final String tooLong = "x".repeat(600);
        assertThatThrownBy(() -> serde().write(null, temporalKey(tooLong), buffer -> {
        }))
                .isInstanceOf(RuntimeException.class);
    }
}
