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

package stroom.gwt.workbench.client.app.rest;

import stroom.widget.form.client.FileUploadCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Answers the file uploads a screen makes from the story's [RestFixtures] (their upload routes,
/// see `RestFixtures.Builder.upload`), the upload equivalent of [FixtureDispatcher]. The
/// harness's upload transport reads the chosen file and passes the upload here.
///
/// Each rendering of a story has its own (and so its own [FixtureSession], so sequences of replies
/// start afresh). The reply is always asynchronous, after the reply's delay, as a real upload's
/// is. Each upload is passed to the [Listener] before its reply is worked out. [#dispose()]
/// cancels the replies still pending and makes this drop any upload that still reaches it.
public final class FixtureUploads {

    private final FixtureSession session;
    private final ReplyScheduler scheduler;
    private final Listener listener;
    private final List<PendingReply> pending = new ArrayList<>();
    private boolean disposed;

    /// @param fixtures  The replies to the uploads.
    /// @param scheduler Runs the replies later.
    /// @param listener  Told about each upload and any problem with it.
    public FixtureUploads(final RestFixtures fixtures, final ReplyScheduler scheduler, final Listener listener) {
        this.session = Objects.requireNonNull(fixtures).newSession();
        this.scheduler = Objects.requireNonNull(scheduler);
        this.listener = Objects.requireNonNull(listener);
    }

    /// Answers an upload from the fixtures, after the reply's delay. An upload that no upload route
    /// matches is reported to the listener and fails as Stroom's transport reports a `404`.
    ///
    /// @param upload   The upload.
    /// @param callback Stroom's callback for the upload (`CustomFileUpload`'s result handler).
    public void upload(final RecordedUpload upload, final FileUploadCallback callback) {
        Objects.requireNonNull(callback);
        if (disposed) {
            // An upload from a screen of an old rendering. It is never answered.
            listener.onDroppedUpload(upload);
            return;
        }
        listener.onUpload(upload);
        final FixtureSession.UploadExchange exchange = session.exchangeUpload(upload);
        if (!exchange.isHandled()) {
            listener.onUnhandledUpload(upload, exchange.getProblem(), session.getFixtures().isStrict());
        }
        final UploadReply reply = exchange.getReply();
        final PendingReply pendingReply = new PendingReply(reply, callback);
        pending.add(pendingReply);
        pendingReply.scheduled = scheduler.schedule(Math.max(1, reply.getDelayMillis()), pendingReply);
    }

    /// Cancels the replies still pending and drops any later upload. Called when the story renders
    /// again.
    public void dispose() {
        disposed = true;
        for (final PendingReply pendingReply : new ArrayList<>(pending)) {
            if (pendingReply.scheduled != null) {
                pendingReply.scheduled.cancel();
            }
        }
        pending.clear();
    }

    /// @return True if [#dispose()] has been called.
    public boolean isDisposed() {
        return disposed;
    }

    /// @return The number of uploads whose replies haven't been delivered yet.
    public int getPendingCount() {
        return pending.size();
    }

    // --------------------------------------------------------------------------------


    /// Told about the uploads a [FixtureUploads] answers.
    public interface Listener {

        /// Called for each upload, before it is answered.
        ///
        /// @param upload The upload.
        void onUpload(RecordedUpload upload);

        /// Called when no upload route matches an upload, which then fails.
        ///
        /// @param upload  The upload.
        /// @param message What to report.
        /// @param strict  True if the story should fail.
        void onUnhandledUpload(RecordedUpload upload, String message, boolean strict);

        /// Called for an upload reaching this after [#dispose()], which is never answered.
        ///
        /// @param upload The upload.
        void onDroppedUpload(RecordedUpload upload);
    }

    // --------------------------------------------------------------------------------


    /// A reply waiting to be delivered.
    private final class PendingReply implements Runnable {

        private final UploadReply reply;
        private final FileUploadCallback callback;
        private ReplyScheduler.Scheduled scheduled;

        private PendingReply(final UploadReply reply, final FileUploadCallback callback) {
            this.reply = reply;
            this.callback = callback;
        }

        @Override
        public void run() {
            pending.remove(this);
            if (!disposed) {
                reply.deliverTo(callback);
            }
        }
    }
}
