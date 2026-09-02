package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.SourceLocation;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestRecordScopeStateSerializer {

    private static SourceLocation location() {
        return SourceLocation.builder(42L)
                .withPartIndex(1L)
                .withRecordIndex(7L)
                .withHighlight(new TextRange(new DefaultLocation(3, 5), new DefaultLocation(3, 20)))
                .build();
    }

    private static RecordScopeState roundTrip(final RecordScopeState state) {
        return RecordScopeStateSerializer.fromBytes(RecordScopeStateSerializer.toBytes(state));
    }

    @Test
    void testRoundTripsBothHalves() {
        final RecordScopeState roundTripped = roundTrip(
                new RecordScopeState(location(), Map.of("user", "jbloggs", "host", "server1")));

        assertThat(roundTripped.sourceLocation()).isEqualTo(location());
        assertThat(roundTripped.scopeMap()).containsExactlyInAnyOrderEntriesOf(
                Map.of("user", "jbloggs", "host", "server1"));
    }

    @Test
    void testEitherHalfCanBeAbsent() {
        assertThat(roundTrip(new RecordScopeState(location(), null)).scopeMap()).isEmpty();
        assertThat(roundTrip(new RecordScopeState(location(), Map.of())).scopeMap()).isEmpty();
        assertThat(roundTrip(new RecordScopeState(null, Map.of("k", "v"))).sourceLocation()).isNull();
        assertThat(roundTrip(new RecordScopeState(null, null)).isEmpty()).isTrue();
    }

    @Test
    void testCountsRoundTrip() {
        final RecordScopeState roundTripped = roundTrip(new RecordScopeState(
                location(), Map.of("k", "v"), Map.of("idEnrichmentFilter", 407L, "other", 0L)));

        assertThat(roundTripped.countFor("idEnrichmentFilter")).hasValue(407L);
        assertThat(roundTripped.countFor("other"))
                .as("zero is a real count, not an absence")
                .hasValue(0L);
        assertThat(roundTripped.scopeMap()).as("the earlier halves still round-trip alongside")
                .containsEntry("k", "v");
    }

    @Test
    void testAnElementThatWasNotCountingIsDistinguishableFromOneThatCountedZero() {
        // The restore treats these differently - an absent element is left alone rather than zeroed - so the
        // serialiser must not collapse them into each other.
        final RecordScopeState roundTripped = roundTrip(
                new RecordScopeState(null, null, Map.of("counted", 0L)));

        assertThat(roundTripped.countFor("counted")).hasValue(0L);
        assertThat(roundTripped.countFor("neverCounted")).isEmpty();
    }

    @Test
    void testStateWrittenBeforeCountersExistedStillReads() {
        // Counters were appended to this format, and a store written by an earlier build is still on disk
        // when the user upgrades mid-session. Truncated state must read back as "no counters", not blow up.
        final byte[] withCounters = RecordScopeStateSerializer.toBytes(
                new RecordScopeState(location(), Map.of("k", "v"), Map.of("e", 3L)));
        final byte[] withoutCounters = RecordScopeStateSerializer.toBytes(
                new RecordScopeState(location(), Map.of("k", "v")));
        // Genuinely old bytes: today's serialiser writes an explicit absent-map marker (one int) even
        // for null counts, so dropping that trailing int reproduces the pre-counter format and forces
        // fromBytes down its "nothing more to read" branch.
        final byte[] truncated = java.util.Arrays.copyOf(
                withoutCounters, withoutCounters.length - Integer.BYTES);
        // The old form is a strict prefix of the new one, which is what makes the append safe.
        assertThat(java.util.Arrays.copyOf(withCounters, truncated.length)).isEqualTo(truncated);

        final RecordScopeState old = RecordScopeStateSerializer.fromBytes(truncated);
        assertThat(old.sourceLocation()).isEqualTo(location());
        assertThat(old.scopeMap()).containsEntry("k", "v");
        assertThat(old.elementCounts()).isEmpty();
    }

    @Test
    void testNullStateStillOccupiesASegment() {
        // A record that captured nothing must still be framed, or the state file stops being index-aligned
        // with the record stream.
        final byte[] bytes = RecordScopeStateSerializer.toBytes(null);
        assertThat(bytes).isNotEmpty();
        assertThat(RecordScopeStateSerializer.fromBytes(bytes).isEmpty()).isTrue();
    }

    @Test
    void testEmptyOrNullBytesReadBackNull() {
        assertThat(RecordScopeStateSerializer.fromBytes(null)).isNull();
        assertThat(RecordScopeStateSerializer.fromBytes(new byte[0])).isNull();
    }

    @Test
    void testAwkwardKeysAndValues() {
        // stroom:put takes arbitrary user data: a null key or value, an empty string, unicode, and values far
        // beyond the 64KB DataOutputStream.writeUTF cap all have to survive.
        final Map<String, String> map = new HashMap<>();
        map.put(null, "nullKey");
        map.put("nullValue", null);
        map.put("", "");
        map.put("unicode é中😀", "value é中😀");
        map.put("big", "x".repeat(100_000));

        final Map<String, String> roundTripped = roundTrip(new RecordScopeState(null, map)).scopeMap();

        assertThat(roundTripped).hasSize(5);
        assertThat(roundTripped.get(null)).isEqualTo("nullKey");
        assertThat(roundTripped.get("nullValue")).isNull();
        assertThat(roundTripped.get("")).isEmpty();
        assertThat(roundTripped.get("unicode é中😀")).isEqualTo("value é中😀");
        assertThat(roundTripped.get("big")).hasSize(100_000);
    }

    @Test
    void testSnapshotIsDetachedFromTheLiveMap() {
        // The live map keeps mutating as later records are processed, so the snapshot must be a copy.
        final Map<String, String> live = new HashMap<>(Map.of("k", "v"));
        final RecordScopeState state = new RecordScopeState(null, live);
        live.put("added", "later");

        assertThat(state.scopeMap()).containsExactlyEntriesOf(Map.of("k", "v"));
    }
}
