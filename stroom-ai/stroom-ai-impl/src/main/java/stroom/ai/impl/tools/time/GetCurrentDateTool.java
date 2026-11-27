/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.ai.impl.tools.time;

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiToolCategory;

import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code get_current_date} - the current date in the user's time zone (or the one asked for).
 */
public class GetCurrentDateTool extends AbstractBuiltInTool {

    /// Makes the tool, which needs nothing injected.
    @Inject
    public GetCurrentDateTool() {
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_current_date")
                .description("Returns the current date in the user's time zone as an ISO-8601 string "
                             + "(YYYY-MM-DD), with the zone. Call this whenever you need to know today's date; "
                             + "do not guess it.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final ToolTimeZone zone;
        try {
            zone = timeZone(request, context);
        } catch (final WorkspaceException e) {
            return ToolResult.text("Error: " + e.getMessage());
        }
        return ToolResult.text(text(Instant.now(), zone));
    }

    /// @param now  Now.
    /// @param zone The call's time zone.
    /// @return Today in the zone, with the zone, e.g. `2026-11-21 (Australia/Canberra)`, and the zone's note, if any.
    public static String text(final Instant now, final ToolTimeZone zone) {
        final String text = LocalDate.ofInstant(now, zone.zone()) + " (" + zone.id() + ")";
        return zone.note() == null
                ? text
                : text + ". " + zone.note();
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
