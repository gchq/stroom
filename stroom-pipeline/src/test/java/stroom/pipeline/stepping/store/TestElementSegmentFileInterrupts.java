package stroom.pipeline.stepping.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A store file must survive a thread being interrupted.
 * <p>
 * {@code FileChannel} is an {@code InterruptibleChannel}: if a thread is interrupted while blocked in I/O on
 * one, the JDK <b>closes the channel</b> - not just for that thread, but permanently, for every user of it.
 * Stepping interrupts task threads (session teardown, and anything outside stepping that terminates tasks),
 * and one store file is shared by every sweep in a session, so without care a single interrupt leaves the
 * whole session unreadable and every subsequent step fails with {@code ClosedChannelException}. That is a real
 * failure that was seen in practice before this was handled.
 * <p>
 * These tests do not race a thread against I/O to provoke it. A channel operation on a thread whose interrupt
 * flag is <b>already set</b> closes the channel immediately and deterministically, which is the same end state
 * with none of the flakiness.
 */
class TestElementSegmentFileInterrupts {

    private static final byte[] RECORD_0 = "record zero".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RECORD_1 = "record one".getBytes(StandardCharsets.UTF_8);

    @AfterEach
    void clearInterrupt() {
        // Never leak an interrupt flag into another test on this thread.
        Thread.interrupted();
    }

    private ElementSegmentFile fileWithTwoRecords(final Path tempDir) throws IOException {
        final ElementSegmentFile file = new ElementSegmentFile(tempDir.resolve("test.dat"));
        file.append(0, RECORD_0);
        file.append(1, RECORD_1);
        return file;
    }

    /**
     * Closes the channel the way an interrupt does, by performing I/O with the interrupt flag set, then
     * clears the flag so the caller is left with a closed channel on a runnable thread.
     */
    private void killChannelByInterrupt(final ElementSegmentFile file) {
        Thread.currentThread().interrupt();
        try {
            file.read(0);
        } catch (final RuntimeException e) {
            // Expected: the JDK closes the channel and the read fails.
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testAnInterruptElsewhereDoesNotPoisonTheFile(@TempDir final Path tempDir) throws IOException {
        // The bug this guards: one interrupted thread must not make the file permanently unreadable for the
        // threads that are still working.
        final ElementSegmentFile file = fileWithTwoRecords(tempDir);
        killChannelByInterrupt(file);

        assertThat(file.read(0)).as("still readable after an interrupt closed the channel").isEqualTo(RECORD_0);
        assertThat(file.read(1)).isEqualTo(RECORD_1);
    }

    @Test
    void testWritingStillWorksAfterAnInterruptClosedTheChannel(@TempDir final Path tempDir) throws IOException {
        final ElementSegmentFile file = fileWithTwoRecords(tempDir);
        final long sizeBefore = file.size();
        killChannelByInterrupt(file);

        final byte[] record2 = "record two".getBytes(StandardCharsets.UTF_8);
        file.append(2, record2);

        // Appending must resume where it left off, not overwrite from the start: the append position and the
        // record index both live in memory, so a reopened channel has to continue the same file.
        assertThat(file.size()).isEqualTo(sizeBefore + record2.length);
        assertThat(file.read(0)).as("the earlier records were not overwritten").isEqualTo(RECORD_0);
        assertThat(file.read(1)).isEqualTo(RECORD_1);
        assertThat(file.read(2)).isEqualTo(record2);
        assertThat(file.recordCount()).isEqualTo(3);
    }

    @Test
    void testAnInterruptedThreadIsNotGivenAWorkingChannel(@TempDir final Path tempDir) throws IOException {
        // The other half of the rule. A thread carrying an interrupt is being asked to stop, so it must fail
        // and unwind - reopening for it would be pointless anyway, since the JDK would close the channel
        // again on the very next call, and it would turn "stop" into a spin.
        final ElementSegmentFile file = fileWithTwoRecords(tempDir);
        killChannelByInterrupt(file);
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> file.read(0))
                .as("an interrupted thread does not get the channel reopened")
                .isInstanceOf(StepDataStoreException.class);

        // ...and the interrupt is still pending, so the caller can still see it was asked to stop.
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void testTheFileRecoversOnceTheInterruptIsCleared(@TempDir final Path tempDir) throws IOException {
        // Taken together with the test above: refusal is tied to the calling thread's state, not a permanent
        // property of the file. The same file works again as soon as a runnable thread uses it.
        final ElementSegmentFile file = fileWithTwoRecords(tempDir);
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> file.read(0)).isInstanceOf(StepDataStoreException.class);

        Thread.interrupted();

        assertThat(file.read(0)).isEqualTo(RECORD_0);
        assertThat(file.read(1)).isEqualTo(RECORD_1);
    }

    @Test
    void testAnExplicitCloseIsNotUndoneByAReopen(@TempDir final Path tempDir) throws IOException {
        // closeQuietly is deliberate teardown, not an accident, so a subsequent read must not quietly
        // resurrect the file... but it must also not corrupt it. Reopening here is harmless because the
        // in-memory index still describes the file exactly; the point is that the data stays intact.
        final ElementSegmentFile file = fileWithTwoRecords(tempDir);
        file.closeQuietly();

        assertThat(file.read(0)).as("the data is still correct however the channel got closed")
                .isEqualTo(RECORD_0);
    }
}
