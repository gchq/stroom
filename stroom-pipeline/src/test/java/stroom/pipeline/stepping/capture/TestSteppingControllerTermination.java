package stroom.pipeline.stepping.capture;

import stroom.meta.shared.Meta;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.pipeline.xsltfunctions.TaskScopeMap;
import stroom.task.api.SimpleTaskContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That a capture can be stopped <b>without interrupting its thread</b>.
 * <p>
 * This matters because of what an interrupt costs. Terminating a task interrupts its thread, and an interrupt
 * during {@code FileChannel} I/O closes that channel permanently for every user of it - and a session's store
 * is shared by all of its sweeps. So a sweep that is merely <i>superseded</i> (the user edited the XSLT, and
 * the in-flight capture is now pointless) must be stopped by a flag the capture loop checks, not by an
 * interrupt: the session keeps its store across an edit, and the next step will read it.
 * <p>
 * The older engine had a different reason to stop - it ran the pipeline per keypress and wanted to quit the
 * moment it reached the record it was after. This engine captures every record, so stopping early is now only
 * ever about abandoning unwanted work, and one more record is a perfectly acceptable price for not killing the
 * file.
 */
class TestSteppingControllerTermination {

    private static final long META_ID = 42L;

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    private SteppingController controller(final Path tempDir) {
        final MetaHolder metaHolder = new MetaHolder();
        metaHolder.setMeta(Meta.builder().id(META_ID).build());
        metaHolder.setPartIndex(0);

        final SteppingController controller = new SteppingController(
                metaHolder, new ErrorReceiverProxy(), new LocationHolder(metaHolder), new TaskScopeMap());
        controller.setTaskContext(new SimpleTaskContext());
        controller.setRequest(PipelineStepRequest.builder().build());
        // No monitors are registered, so a captured record simply writes nothing - this test is about the
        // decision to stop, not about what gets stored.
        controller.setCaptureTarget(
                new StepDataStore(tempDir.resolve(Long.toString(META_ID)), new SteppingConfig()),
                new ElementFingerprints(Map.of(), Map.of()),
                null);
        return controller;
    }

    @Test
    void testACaptureKeepsGoingWhileItIsWanted(@TempDir final Path tempDir) {
        final SteppingController controller = controller(tempDir);
        controller.setTerminateCheck(() -> false);

        assertThat(controller.endRecord(0))
                .as("endRecord returns false to mean carry on")
                .isFalse();
    }

    @Test
    void testTheFlagAloneStopsTheCapture(@TempDir final Path tempDir) {
        // The whole point: no interrupt anywhere, and the capture still stops.
        final SteppingController controller = controller(tempDir);
        controller.setTerminateCheck(() -> true);

        assertThat(controller.endRecord(0))
                .as("a raised terminate flag stops the capture at the next record")
                .isTrue();
        assertThat(Thread.currentThread().isInterrupted())
                .as("and it did so without the thread being interrupted")
                .isFalse();
    }

    @Test
    void testTheFlagIsRereadEveryRecord(@TempDir final Path tempDir) {
        // A check made once at the start of the capture would be useless: a sweep is superseded while it is
        // running, so the flag has to be read as records go past.
        final boolean[] abandoned = {false};
        final SteppingController controller = controller(tempDir);
        controller.setTerminateCheck(() -> abandoned[0]);

        assertThat(controller.endRecord(0)).as("record 0 captured").isFalse();
        assertThat(controller.endRecord(1)).as("record 1 captured").isFalse();

        abandoned[0] = true;

        assertThat(controller.endRecord(2)).as("stops as soon as the flag is raised").isTrue();
    }

    @Test
    void testAStreamSweepsOwnFlagIsWhatDrivesIt(@TempDir final Path tempDir) {
        // Wired the way the drivers wire it, so this covers the real supplier rather than a stand-in.
        final StreamSweep sweep = new StreamSweep(
                META_ID, new StepDataStore(tempDir.resolve("sweep"), new SteppingConfig()));
        final SteppingController controller = controller(tempDir);
        controller.setTerminateCheck(sweep::isTerminateRequested);

        assertThat(controller.endRecord(0)).isFalse();

        sweep.requestTerminate();

        assertThat(controller.endRecord(1))
                .as("requestTerminate alone stops the capture - no task termination involved")
                .isTrue();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void testNoTerminateCheckMeansKeepGoing(@TempDir final Path tempDir) {
        // The default must be permissive; a controller nobody wired a check into must not stop capturing.
        final SteppingController controller = controller(tempDir);

        assertThat(controller.endRecord(0)).isFalse();

        controller.setTerminateCheck(null);
        assertThat(controller.endRecord(1)).as("a null check is treated as 'not terminated'").isFalse();
    }
}
