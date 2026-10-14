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

package stroom.ai.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/**
 * The state of an "Ask Stroom AI" agent run, i.e. the work of answering one question in a chat.
 */
public enum AiChatRunStatus implements HasPrimitiveValue, HasDisplayValue {

    /**
     * Waiting for a free slot, as the user or node is at its limit of runs.
     */
    QUEUED(0, "Queued", true),
    /**
     * Being worked on.
     */
    RUNNING(1, "Running", true),
    /**
     * Paused until the user does something, e.g. approves a tool call or signs in again.
     */
    WAITING_FOR_USER(2, "Needs you", true),
    /**
     * Asked to stop, and will stop at the next safe point.
     */
    STOPPING(3, "Stopping", true),
    /**
     * Stopped before it finished. It can be resumed.
     */
    STOPPED(4, "Stopped", false),
    /**
     * The node running it was lost (it stopped sending heartbeats). It can be resumed.
     */
    INTERRUPTED(5, "Interrupted", false),
    /**
     * Ended with an error.
     */
    FAILED(6, "Failed", false),
    /**
     * Finished.
     */
    DONE(7, "Done", false);

    public static final PrimitiveValueConverter<AiChatRunStatus> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiChatRunStatus.class, AiChatRunStatus.values());

    private final byte primitiveValue;
    private final String displayValue;
    private final boolean active;

    AiChatRunStatus(final int primitiveValue, final String displayValue, final boolean active) {
        this.primitiveValue = (byte) primitiveValue;
        this.displayValue = displayValue;
        this.active = active;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * @return True if the run has not ended, so no other run may start in its chat.
     */
    public boolean isActive() {
        return active;
    }
}
