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

package stroom.document.client.editor;

import java.util.ArrayList;
import java.util.List;

/// Makes a document's saves wait for each other. A save sends the version the last one was given back,
/// so one sent before then is refused by the server, as if someone else had changed the document.
///
/// Whoever starts a save must say how it ended, whatever happens, or no other will ever start.
public final class SaveQueue {

    private final List<Waiting> waiting = new ArrayList<>();
    private boolean saving;

    /// Starts a save, unless one is in progress, when it is tried again once that has finished.
    ///
    /// @param again      Saves what is to be saved by then, if anything.
    /// @param onAbandoned What to run instead if the save in progress fails, so there is no point.
    /// @return Whether it was started, for the caller to say when it has succeeded or failed.
    public boolean start(final Runnable again, final Runnable onAbandoned) {
        if (saving) {
            waiting.add(new Waiting(again, onAbandoned));
            return false;
        }
        saving = true;
        return true;
    }

    /// Does part of the save in progress. If it throws the save has failed, so that a fault in it doesn't
    /// stop every later save.
    ///
    /// @param step What to do.
    public void guard(final Runnable step) {
        boolean done = false;
        try {
            step.run();
            done = true;
        } finally {
            if (!done) {
                failed();
            }
        }
    }

    /// The save in progress has succeeded: what was saved is read, then those waiting are tried again.
    /// If it can't be read those waiting are abandoned.
    ///
    /// @param read Reads what was saved.
    public void succeeded(final Runnable read) {
        guard(read);
        runAll(finish().stream().map(Waiting::again).toList());
    }

    /// The save in progress has failed, or was given up: those waiting are too.
    public void failed() {
        runAll(finish().stream().map(Waiting::onAbandoned).toList());
    }

    // Runs each, though one before it throws, then throws what the first threw
    private static void runAll(final List<Runnable> runnables) {
        RuntimeException first = null;
        for (final Runnable runnable : runnables) {
            try {
                runnable.run();
            } catch (final RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private List<Waiting> finish() {
        saving = false;
        final List<Waiting> finished = List.copyOf(waiting);
        waiting.clear();
        return finished;
    }


    // --------------------------------------------------------------------------------


    private record Waiting(Runnable again, Runnable onAbandoned) {

    }
}
