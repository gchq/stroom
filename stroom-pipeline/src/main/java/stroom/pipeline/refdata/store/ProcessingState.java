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

package stroom.pipeline.refdata.store;

public enum ProcessingState {

    // byte values MUST be unique and existing ones MUST NOT be changed
    // or it will break de-serialisation of existing data.

    /**
     * A thread is busy loading this ref stream at the moment
     */
    LOAD_IN_PROGRESS((byte) 0, "Load In Progress"),
    /**
     * A thread is busy puring this ref stream at the moment
     */
    PURGE_IN_PROGRESS((byte) 1, "Purge In Progress"),
    /**
     * The load of this ref stream completed successfully
     */
    COMPLETE((byte) 2, "Complete"),
    /**
     * The load of this ref stream failed due to an error, e.g. parsing the reference data XML.
     * It is incomplete and/or wrong so should not be used. No point in retrying.
     */
    FAILED((byte) 3, "Failed"),
    /**
     * The load was terminated, i.e. by task cancellation or system shutdown.
     * It is incomplete and/or wrong so should not be used, but we can retry later.
     */
    TERMINATED((byte) 4, "Terminated"),
    /**
     * The purge of this ref stream failed for some unexpected error.
     */
    PURGE_FAILED((byte) 5, "Purge Failed"),
    /**
     * The reference entries have been staged in the temporary store and are awaiting transfer
     * into the reference store.
     */
    STAGED((byte) 6, "Entries Staged"),
    /**
     * Explicitly marked for purge.
     */
    READY_FOR_PURGE((byte) 7, "Ready for Purge")
    ;

    private final byte id;
    private final String displayName;

    private static final ProcessingState[] states = new ProcessingState[ProcessingState.values().length];

    static {
        for (final ProcessingState state : ProcessingState.values()) {
            if (states[state.getId()] != null) {
                throw new RuntimeException("ID " + state.getId() + " is already in use");
            }
            states[state.getId()] = state;
        }
    }

    ProcessingState(final byte id, final String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public byte getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProcessingState fromByte(final byte id) {
        return states[id];
    }
}
