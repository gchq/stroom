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

package stroom.pipeline.stepping.capture;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.StorePin;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The run harness the two capture drivers share. In order: publish the task context and honour a
 * termination requested before the start (the context is published <b>before</b> the flag is read - the
 * session writes the flag before reading the context, so whichever runs second sees the other's write and
 * a sweep cannot start after its session has closed); pin the fingerprint versions the run reads and
 * writes, so an edit made mid-run cannot push them out of the retention window underneath it; enter the
 * stepping permission and read scope as the requesting user, wire the error receiver, and point the
 * controller at the sweep; then, after the body, report the sweep's terminal state. Only a run that went
 * to the end unfailed and unterminated is marked fully captured - a reader treats "ended" as "every record
 * this producer will ever signal is in the store" and would navigate silently past the end of a truncated
 * one. The catch is {@link Throwable}, not {@code RuntimeException}: an {@link Error} (OOM is plausible on
 * a large stream) would otherwise leave the sweep neither complete nor errored, hanging every reader on it.
 */
final class SweepRun {

    private final SecurityContext securityContext;
    private final CurrentUserHolder currentUserHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final SteppingController controller;

    SweepRun(final SecurityContext securityContext,
             final CurrentUserHolder currentUserHolder,
             final ErrorReceiverProxy errorReceiverProxy,
             final SteppingController controller) {
        this.securityContext = securityContext;
        this.currentUserHolder = currentUserHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.controller = controller;
    }

    /**
     * @param what           names the run in error messages, e.g. "Stepping capture".
     * @param taskInfo       shown on the task context, or null for none.
     * @param pinStores      every store the run reads or writes; usually one, both ends for a reprocess.
     * @param receiverSink   hands the run's error receiver back to the driver, which reads indicators off it.
     * @param body           the driver-specific work; record failures via the driver's own state rather than
     *                       throwing where the original behaviour was to carry on to the epilogue.
     * @param pendingFailure the body's recorded failure, if any - it marks the sweep errored in preference
     *                       to marking it complete.
     */
    void execute(final TaskContext taskContext,
                 final PipelineStepRequest request,
                 final StreamSweep sweep,
                 final ElementFingerprints fingerprints,
                 final List<StepDataStore> pinStores,
                 final String what,
                 final String taskInfo,
                 final Consumer<LoggingErrorReceiver> receiverSink,
                 final Runnable body,
                 final Supplier<Throwable> pendingFailure) {
        sweep.setTaskContext(taskContext);
        if (sweep.isTerminateRequested()) {
            sweep.markError(new RuntimeException(
                    what + " of stream " + sweep.getMetaId() + " was terminated before it started"));
            return;
        }
        if (taskInfo != null) {
            taskContext.info(() -> taskInfo);
        }

        final List<StorePin> pins = new ArrayList<>(pinStores.size());
        try {
            for (final StepDataStore store : pinStores) {
                pins.add(store.pin(fingerprints.getCumulativeFingerprints()));
            }
            securityContext.secure(AppPermission.STEPPING_PERMISSION, () ->
                    securityContext.useAsRead(() -> {
                        currentUserHolder.setCurrentUser(securityContext.getUserIdentity());

                        final LoggingErrorReceiver receiver = new LoggingErrorReceiver();
                        errorReceiverProxy.setErrorReceiver(receiver);
                        receiverSink.accept(receiver);

                        controller.setRequest(request);
                        controller.setTaskContext(taskContext);
                        // A superseded sweep is abandoned by flag rather than interrupt, so the controller
                        // has to watch the flag to stop at the next record.
                        controller.setTerminateCheck(sweep::isTerminateRequested);
                        // Capture mode: persist each record atomically and advance the progress signal.
                        controller.setCaptureTarget(sweep.getStore(), fingerprints, sweep::recordCaptured);

                        body.run();
                    }));

            final Throwable failure = pendingFailure.get();
            if (failure != null) {
                sweep.markError(failure);
            } else if (taskContext.isTerminated() || sweep.isTerminateRequested()) {
                sweep.markError(new RuntimeException(
                        what + " of stream " + sweep.getMetaId() + " was terminated"));
            } else {
                sweep.markFullyCaptured();
            }
        } catch (final Throwable t) {
            sweep.markError(t);
            throw t;
        } finally {
            for (int i = pins.size() - 1; i >= 0; i--) {
                pins.get(i).close();
            }
        }
    }
}
