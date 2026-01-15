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

package stroom.util.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ErrorMessages {

    private final List<ErrorMessage> errorMessages;

    public ErrorMessages(final List<ErrorMessage> errorMessages) {
        this.errorMessages = errorMessages == null ? Collections.emptyList() : errorMessages;
    }

    public List<ErrorMessage> getErrorMessages() {
        return errorMessages;
    }

    public Severity getHighestSeverity() {
        return asMap().keySet().stream().sorted(Severity.HIGH_TO_LOW_COMPARATOR).findFirst().orElse(null);
    }

    public boolean containsAny(final Severity...severities) {
        for (final Severity severity : severities) {
            if (!get(severity).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return errorMessages.isEmpty();
    }

    public List<ErrorMessage> get(final Severity...severities) {
        final List<ErrorMessage> messages = new ArrayList<>();
        for (final Severity severity : severities) {
            messages.addAll(asMap().getOrDefault(severity, new ArrayList<>()));
        }
        return messages;
    }

    public List<ErrorMessage> getErrorMessagesOrderedBySeverity() {
        return errorMessages.stream()
                .sorted(Comparator.comparing(e -> ((ErrorMessage) e).getSeverity().getId()).reversed())
                .collect(Collectors.toList());
    }

    private Map<Severity, List<ErrorMessage>> asMap() {
        return errorMessages.stream()
                .collect(Collectors.groupingBy(ErrorMessage::getSeverity, HashMap::new,
                        Collectors.mapping(Function.identity(), Collectors.toList())));
    }
}
