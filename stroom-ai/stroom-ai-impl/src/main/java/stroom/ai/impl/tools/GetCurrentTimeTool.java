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

import stroom.ai.shared.AiToolCategory;

import jakarta.inject.Inject;

import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * {@code get_current_time} - the current time of day, in UTC.
 */
class GetCurrentTimeTool implements StroomTool {

    @Inject
    GetCurrentTimeTool() {
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_current_time")
                .description("Returns the current time of day in UTC as an ISO-8601 string "
                             + "(HH:MM:SSZ, 24-hour). Call this whenever you need to know the current "
                             + "time; do not guess it.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        return ToolResult.text(OffsetTime.now(ZoneOffset.UTC)
                                .truncatedTo(ChronoUnit.SECONDS)
                                .format(DateTimeFormatter.ISO_OFFSET_TIME));
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
