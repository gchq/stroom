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

package stroom.ai.api;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * A simple token count estimator that approximates token counts based on character length.
 * This is used for document splitting operations where exact token counting is not required.
 */
public class SimpleTokenCountEstimator implements TokenCountEstimator {

    @Override
    public int estimateTokenCountInText(final String text) {
        return text.length() / 4;
    }

    @Override
    public int estimateTokenCountInMessage(final ChatMessage message) {
        return estimateTokenCountInText(message.toString());
    }

    @Override
    public int estimateTokenCountInMessages(final Iterable<ChatMessage> messages) {
        int total = 0;
        for (final ChatMessage message : messages) {
            total += estimateTokenCountInMessage(message);
        }
        return total;
    }
}
