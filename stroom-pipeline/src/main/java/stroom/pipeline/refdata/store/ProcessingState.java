/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.pipeline.refdata.store;

public enum ProcessingState {

    // byte values must be unique obviously
    LOAD_IN_PROGRESS((byte)0, "Load In Progress"),
    PURGE_IN_PROGRESS((byte)1, "Purge In Progress"),
    COMPLETE((byte)2, "Complete");

    private final byte id;
    private final String displayName;

    private static ProcessingState[] states = new ProcessingState[ProcessingState.values().length];

    static {
        for (ProcessingState state : ProcessingState.values()) {
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
