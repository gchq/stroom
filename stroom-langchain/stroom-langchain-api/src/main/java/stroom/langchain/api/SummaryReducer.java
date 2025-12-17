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

package stroom.langchain.api;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.memory.ChatMemoryAccess;

public interface SummaryReducer extends ChatMemoryAccess {

    String USER_MESSAGE = """
                Merge the following TWO summaries into a single improved summary.
                Preserve important details and remove duplicates.

                SUMMARY A:
                {{a}}

                SUMMARY B:
                {{b}}
            """;

    @SystemMessage("You merge partial answers into a unified, concise summary.")
    @UserMessage(USER_MESSAGE)
    String merge(@MemoryId String memoryId, @V("a") String a, @V("b") String b);
}
