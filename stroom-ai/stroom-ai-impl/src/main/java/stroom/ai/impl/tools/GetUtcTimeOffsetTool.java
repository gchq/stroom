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
import stroom.ai.shared.AiToolCategory;

import jakarta.inject.Inject;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * {@code get_utc_time_offset} - the current UTC offset for a time zone, so local times can be converted
 * to and from UTC.
 */
class GetUtcTimeOffsetTool extends AbstractBuiltInTool {

    private static final String ARG_TIME_ZONE = "timeZone";

    @Inject
    GetUtcTimeOffsetTool() {
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_utc_time_offset")
                .description("Returns the current offset from UTC (e.g. '-05:00') for a time zone, so a "
                             + "local time in that zone can be converted to or from UTC. Daylight saving "
                             + "is taken into account. If no time zone is given, the Stroom server's "
                             + "default time zone is used.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(ARG_TIME_ZONE,
                                "An IANA time zone ID such as 'Europe/London' or 'America/New_York', "
                                + "or a fixed offset such as '+02:00'. Optional.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final String requested = stringArg(request, ARG_TIME_ZONE);

        final ZoneId zone;
        try {
            zone = requested == null
                    ? ZoneId.systemDefault()
                    : ZoneId.of(requested);
        } catch (final DateTimeException e) {
            return ToolResult.text("Unknown time zone '" + requested + "'. Use an IANA zone ID like 'Europe/London' "
                                   + "or a fixed offset like '+02:00'.");
        }

        final ZoneOffset offset = zone.getRules().getOffset(Instant.now());
        final String offsetText = ZoneOffset.UTC.equals(offset)
                ? "+00:00"
                : offset.getId();
        return ToolResult.text(zone.getId() + " is currently " + offsetText + " from UTC.");
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
