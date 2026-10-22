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

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * {@code get_current_date} - the current date, in UTC.
 */
class GetCurrentDateTool implements StroomTool {

    @Inject
    GetCurrentDateTool() {
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_current_date")
                .description("Returns the current date in UTC as an ISO-8601 string (YYYY-MM-DD). "
                             + "Call this whenever you need to know today's date; do not guess it.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        return ToolResult.text(LocalDate.now(ZoneOffset.UTC).toString());
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
