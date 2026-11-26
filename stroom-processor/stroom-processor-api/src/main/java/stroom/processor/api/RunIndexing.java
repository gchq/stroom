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

package stroom.processor.api;

import java.util.Optional;

/// What an index says of one run of a processor task while it is going, which decides how the run ends. Of all that
/// it says, the worst wins: documents lost, then could not index, then hold. Anything that ends the run in another
/// way, e.g. its node stopping, wins over all of them.
///
/// Every method can be called from any thread, any number of times.
public class RunIndexing {

    private boolean held;
    private String couldNotIndexReason;
    private boolean couldNotIndexCounted;
    private boolean documentsLost;

    /// Says that what the run indexed is waiting to be committed, so its task is to be set to `Committing` rather
    /// than `Complete`. The index then completes it, or puts it back, with [HeldProcessorTaskService].
    public synchronized void hold() {
        held = true;
    }

    /// Says that the stream could not be indexed, so the run is to end as if it had hit a fault.
    ///
    /// If this is called more than once, the fault is counted if any call says so, and the reason is that of the
    /// first call that decides whether it is counted, so that the reason shown always explains why.
    ///
    /// @param reason  Why it could not be indexed, to be shown with the task.
    /// @param counted True if the cause is in the stream, so the fault is counted towards those that abort the task.
    ///                False if it isn't, e.g. the index could not be written to, so every task of the filter would hit
    ///                it. This then decides even if the pipeline threw an exception too.
    public synchronized void couldNotIndex(final String reason, final boolean counted) {
        if (couldNotIndexReason == null || (counted && !couldNotIndexCounted)) {
            couldNotIndexReason = reason == null
                    ? "Could not index the stream"
                    : reason;
        }
        couldNotIndexCounted |= counted;
    }

    /// Says that documents the run indexed have been lost, so the task is to be put back to be run again, without
    /// counting a fault.
    public synchronized void documentsLost() {
        documentsLost = true;
    }

    /// Finds out whether the index has asked for the task to be held as `Committing`.
    ///
    /// @return True if it has.
    public synchronized boolean isHeld() {
        return held;
    }

    /// Gets why the stream could not be indexed, if the index has said that it couldn't.
    ///
    /// @return The reason, or empty if the index hasn't said so.
    public synchronized Optional<String> getCouldNotIndexReason() {
        return Optional.ofNullable(couldNotIndexReason);
    }

    /// Finds out whether any time the index said that the stream could not be indexed was to be counted as a fault.
    ///
    /// @return True if it was.
    public synchronized boolean isCouldNotIndexCounted() {
        return couldNotIndexCounted;
    }

    /// Finds out whether the index has said that documents the run indexed have been lost.
    ///
    /// @return True if it has.
    public synchronized boolean isDocumentsLost() {
        return documentsLost;
    }
}
