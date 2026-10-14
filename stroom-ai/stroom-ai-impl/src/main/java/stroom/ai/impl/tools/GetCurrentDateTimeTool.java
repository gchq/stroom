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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * {@code get_current_datetime} - the current date and time, in UTC.
 */
class GetCurrentDateTimeTool implements BuiltInTool {

    @Inject
    GetCurrentDateTimeTool() {
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("get_current_datetime")
                .description("Returns the current date and time in UTC as an ISO-8601 string "
                             + "(YYYY-MM-DDThh:mm:ssZ). Call this whenever you need to know the current "
                             + "date and time; do not guess it.")
                .build();
    }

    @Override
    public String execute(final ToolExecutionRequest request) {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
