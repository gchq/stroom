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

package stroom.core.client.event;

import stroom.util.shared.NullSafe;

public class CloseContentEvent {

    private final DirtyMode dirtyMode;
    private final Callback callback;

    public CloseContentEvent(final DirtyMode dirtyMode,
                             final Callback callback) {
        this.dirtyMode = NullSafe.requireNonNullElse(dirtyMode, DirtyMode.CONFIRM_DIRTY);
        this.callback = callback;
    }

    public DirtyMode getDirtyMode() {
        return dirtyMode;
    }

    public Callback getCallback() {
        return callback;
    }


    // --------------------------------------------------------------------------------


    public interface Handler {

        void onCloseRequest(CloseContentEvent event);
    }


    // --------------------------------------------------------------------------------


    public interface Callback {

        void closeTab(boolean ok);
    }


    // --------------------------------------------------------------------------------


    public enum DirtyMode {
        /**
         * Close regardless of dirty state, losing changes (e.g. when deleting)
         */
        FORCE,
        /**
         * Do nothing with documents that are dirty
         */
        SKIP_DIRTY,
        /**
         * Get the user to confirm the closure if dirty
         */
        CONFIRM_DIRTY,
        ;
    }
}
