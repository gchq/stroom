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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiToolCategory;

import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * {@code get_current_datetime} - the current date and time in the user's time zone (or the one asked for), with
 * the zone, and in UTC.
 */
class GetCurrentDateTimeTool extends AbstractBuiltInTool {

    @Inject
    GetCurrentDateTimeTool() {
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_current_datetime")
                .description("Returns the current date and time in the user's time zone (ISO-8601 with its "
                             + "offset and zone) and in UTC. Call this whenever you need to know the current "
                             + "date and time; do not guess it.")
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
        return ToolResult.text(text(Instant.now().truncatedTo(ChronoUnit.SECONDS), zone));
    }

    /// @param now  Now.
    /// @param zone The call's time zone.
    /// @return Now in the zone, with the zone, then in UTC, e.g. `2026-11-21T10:42:00+11:00 (Australia/Canberra);
    /// UTC 2026-11-20T23:42:00Z`, and the zone's note, if any.
    static String text(final Instant now, final ToolTimeZone zone) {
        final String utc = now.toString();
        final String text = zone.isUtc()
                ? utc + " (UTC)"
                : zone.format(now) + " (" + zone.id() + "); UTC " + utc;
        return zone.note() == null
                ? text
                : text + ". " + zone.note();
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
