package stroom.pipeline.stepping.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A store file must be able to hold records it was never given in order, with gaps between them.
 * <p>
 * A full sweep writes 0, 1, 2..., and the file used to assume exactly that - it derived a record's bytes from
 * its <i>position</i> in the file. Materialising a single record on demand (see {@code stepping-design.md}
 * §11) breaks that assumption completely: it writes record N into an otherwise empty file, then perhaps
 * record M with nothing in between. Records are therefore keyed by index rather than by position.
 * <p>
 * Contiguity is still what a sweep produces, and callers that require it check for it themselves; the file
 * simply no longer depends on it.
 */
class TestElementSegmentFileSparse {

    private static byte[] bytes(final String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private ElementSegmentFile file(final Path tempDir) throws IOException {
        return new ElementSegmentFile(tempDir.resolve("test.dat"));
    }

    @Test
    void testASingleRecordDeepIntoAStream(@TempDir final Path tempDir) throws IOException {
        // The mid-point replay case: nothing else has been written, and record 100,000 is materialised alone.
        final ElementSegmentFile file = file(tempDir);
        file.append(100_000, bytes("the record the user is looking at"));

        assertThat(file.read(100_000)).isEqualTo(bytes("the record the user is looking at"));
        assertThat(file.contains(100_000)).isTrue();
        assertThat(file.recordCount()).isEqualTo(1);
    }

    @Test
    void testGapsBetweenRecords(@TempDir final Path tempDir) throws IOException {
        // Stepping about with FORWARD/BACKWARD materialises whatever the user visits, in whatever order.
        final ElementSegmentFile file = file(tempDir);
        file.append(10, bytes("ten"));
        file.append(50, bytes("fifty"));
        file.append(11, bytes("eleven"));

        assertThat(file.read(10)).isEqualTo(bytes("ten"));
        assertThat(file.read(11)).isEqualTo(bytes("eleven"));
        assertThat(file.read(50)).isEqualTo(bytes("fifty"));
        assertThat(file.recordCount()).isEqualTo(3);
    }

    @Test
    void testAGapIsNotMistakenForAHeldRecord(@TempDir final Path tempDir) throws IOException {
        // The important one: a record inside the file's span that was never written must report as absent,
        // or a step would be served whatever bytes happened to sit at the computed offset.
        final ElementSegmentFile file = file(tempDir);
        file.append(10, bytes("ten"));
        file.append(50, bytes("fifty"));

        assertThat(file.contains(30)).as("a gap is not held").isFalse();
        assertThat(file.contains(9)).isFalse();
        assertThat(file.contains(51)).isFalse();
        assertThatThrownBy(() -> file.read(30))
                .as("reading a gap fails loudly rather than returning a neighbour's bytes")
                .isInstanceOf(StepDataStoreException.class);
    }

    @Test
    void testOutOfOrderWritesReadBackCorrectly(@TempDir final Path tempDir) throws IOException {
        // Bytes are appended in call order but addressed by record index, so descending writes must still
        // read back as themselves - this is what a BACKWARD walk produces.
        final ElementSegmentFile file = file(tempDir);
        file.append(5, bytes("five"));
        file.append(4, bytes("four"));
        file.append(3, bytes("three"));

        assertThat(file.read(3)).isEqualTo(bytes("three"));
        assertThat(file.read(4)).isEqualTo(bytes("four"));
        assertThat(file.read(5)).isEqualTo(bytes("five"));
    }

    @Test
    void testContiguousWritesStillBehaveExactlyAsBefore(@TempDir final Path tempDir) throws IOException {
        // What every full sweep does; the sparse support must not have changed it.
        final ElementSegmentFile file = file(tempDir);
        for (int i = 0; i < 5; i++) {
            file.append(i, bytes("record " + i));
        }

        for (int i = 0; i < 5; i++) {
            assertThat(file.read(i)).isEqualTo(bytes("record " + i));
        }
        assertThat(file.recordCount()).isEqualTo(5);
        assertThat(file.contains(5)).isFalse();
        assertThat(file.nextRecordIndex()).as("still the next contiguous index").isEqualTo(5);
    }

    @Test
    void testANonZeroBaseIsStillHonoured(@TempDir final Path tempDir) throws IOException {
        // Reader and text elements are 1-based; the first index written is the base whether or not it is 0.
        final ElementSegmentFile file = file(tempDir);
        file.append(1, bytes("one"));
        file.append(2, bytes("two"));

        assertThat(file.contains(0)).isFalse();
        assertThat(file.read(1)).isEqualTo(bytes("one"));
        assertThat(file.nextRecordIndex()).isEqualTo(3);
    }

    @Test
    void testRecordsOfDifferentLengths(@TempDir final Path tempDir) throws IOException {
        // Lengths are stored per record rather than inferred from the neighbouring offset, so a short record
        // between two long ones must not bleed into either.
        final ElementSegmentFile file = file(tempDir);
        file.append(0, bytes("x".repeat(1000)));
        file.append(1, bytes("y"));
        file.append(2, bytes("z".repeat(500)));

        assertThat(file.read(0)).hasSize(1000);
        assertThat(file.read(1)).isEqualTo(bytes("y"));
        assertThat(file.read(2)).hasSize(500);
    }
}
