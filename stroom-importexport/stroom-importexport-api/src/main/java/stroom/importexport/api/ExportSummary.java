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

package stroom.importexport.api;

import stroom.util.shared.Message;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExportSummary {

    private final Map<String, Integer> successCountsByType = new HashMap<>();
    private final Map<String, Integer> failedCountsByType = new HashMap<>();
    private List<Message> messages = Collections.emptyList();

    public void addSuccess(final String type) {
        successCountsByType.merge(type, 1, Integer::sum);
    }

    public void addFailure(final String type) {
        failedCountsByType.merge(type, 1, Integer::sum);
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(final List<Message> messages) {
        this.messages = messages;
    }

    public Map<String, Integer> getFailedCountsByType() {
        return failedCountsByType;
    }

    public Map<String, Integer> getSuccessCountsByType() {
        return successCountsByType;
    }

    public int getSuccessTotal() {
        return successCountsByType.values()
                .stream()
                .mapToInt(i -> i)
                .sum();
    }

    public int getFailedTotal() {
        return failedCountsByType.values()
                .stream()
                .mapToInt(i -> i)
                .sum();
    }

    @Override
    public String toString() {
        return "ExportSummary {" +
               "\n  successCountsByType=" + successCountsByType +
               "\n  failedCountsByType=" + failedCountsByType +
               "\n  messages=" + messages +
               "\n}";
    }

}
