/*
 * Copyright 2025 Crown Copyright
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

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum AiMessageType implements HasPrimitiveValue {
    USER_MESSAGE(0),
    AI_RESPONSE(1),
    WORKING(2),
    ERROR(3),
    DASHBOARD_DATA(4),
    QUERY_DATA(5),
    TABLE_DATA(6),
    ATTACHMENT(7),
    DEBUG_DETAIL(8),
    THINKING(9),
    /**
     * Something went wrong that did not stop the question being answered, e.g. an MCP tool server that
     * could not be reached. Shown to the user but not sent to the model.
     */
    WARNING(10),
    /**
     * A message the user sent while an agent run was working on their question, to steer it. It belongs to the
     * question's turn, and the run picks it up at its next safe point.
     */
    STEER_MESSAGE(11),
    /**
     * A tool the model called, as JSON (see {@code ToolCallRecord}).
     */
    TOOL_CALL(12),
    /**
     * The result of a tool call, as JSON (see {@code ToolResultRecord}). Large results are saved to a workspace
     * file and only a short stub is held here.
     */
    TOOL_RESULT(13),
    /**
     * A short record of a finished turn, built in code (question, tools used and their small results exactly,
     * files created, and the start of the answer), used in place of the turn once it has been compacted.
     */
    TURN_DIGEST(14),
    /**
     * A record of the chat being compacted, as JSON (see {@code CompactionRecord}): which turns the model now
     * sees as digests, and any summary of the oldest of them.
     */
    COMPACTION_SUMMARY(15),
    /**
     * A change the agent made to memory, as JSON (see {@code MemoryChange}), shown to the user as a card.
     */
    MEMORY_UPDATE(16),
    /**
     * Something the agent wants to do that needs the user's approval first, as JSON (see {@code MemoryChange}
     * for a memory change). Shown to the user as a card to accept or reject.
     */
    APPROVAL_REQUEST(17),
    /**
     * The answer so far, while the model is still writing it. Updated in place, and removed once the answer is
     * stored as an {@link #AI_RESPONSE}. Never sent to the model.
     */
    STREAMING_ANSWER(18),
    /**
     * The model's reasoning so far, while it is still thinking. Updated in place, and removed once the reasoning
     * is stored as a {@link #THINKING} message. Never sent to the model.
     */
    STREAMING_THINKING(19),
    /**
     * A form an MCP tool asked the user to fill in while it ran (MCP elicitation), as JSON (see
     * {@code ElicitationRecord}): the server's message and form, and once answered, the answer. The answer goes to
     * the MCP server, never to the model, so these messages are never sent to the model.
     */
    ELICITATION(20),
    /**
     * Up to three things the user might ask next, as a JSON array of strings, suggested after an answer. Shown as
     * links above the input while the answer is the latest thing in the chat; never shown in the conversation or
     * sent to the model.
     */
    SUGGESTIONS(21),
    /**
     * The agent's task list for the turn, as JSON ({@code {"tasks":[{"title":..,"status":..}]}}), written with its
     * task_list tool. The latest is shown above the input while its turn is the latest; never sent to the model
     * (the tool call and result are).
     */
    TASK_LIST(22);

    public static final PrimitiveValueConverter<AiMessageType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiMessageType.class, AiMessageType.values());

    private final byte primitiveValue;

    AiMessageType(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
