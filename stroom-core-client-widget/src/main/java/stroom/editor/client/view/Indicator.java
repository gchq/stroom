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

package stroom.editor.client.view;

import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Stores details of warnings, errors and fatal errors.
 */
public class Indicator implements Serializable {

    private static final long serialVersionUID = 257135216859640487L;

    private final Map<Severity, Set<StoredError>> errorMap = new HashMap<>();

    public Indicator() {
    }

    public Indicator(final Indicator indicator) {
        addAll(indicator);
    }

    public void addAll(final Indicator indicator) {
        if (indicator != null) {
            for (final Entry<Severity, Set<StoredError>> entry : indicator.errorMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue().size() > 0) {
                    Set<StoredError> messages = errorMap.get(entry.getKey());
                    if (messages == null) {
                        messages = new HashSet<>(entry.getValue().size());
                    }

                    if (entry.getValue() != null) {
                        messages.addAll(entry.getValue());
                    }
                }
            }
        }
    }

    public void add(final Severity severity, final StoredError message) {
        errorMap.computeIfAbsent(severity, k -> new HashSet<>()).add(message);
    }

    public Map<Severity, Set<StoredError>> getErrorMap() {
        return errorMap;
    }

    public Severity getMaxSeverity() {
        for (final Severity sev : Severity.SEVERITIES) {
            final Set<StoredError> messages = errorMap.get(sev);
            if (NullSafe.hasItems(messages)) {
                return sev;
            }
        }
        return null;
    }

    public int getCount(final Severity severity) {
        final Set<StoredError> storedErrors = errorMap.get(severity);
        return storedErrors != null
                ? storedErrors.size()
                : 0;
    }

    public boolean isEmpty() {
        return errorMap.isEmpty();
    }

    public int size() {
        return errorMap.values()
                .stream()
                .mapToInt(Set::size)
                .sum();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Severity severity : Severity.SEVERITIES) {
            final Set<StoredError> messages = errorMap.get(severity);
            if (messages != null) {
                for (final StoredError message : messages) {
                    sb.append(message);
                    sb.append("\n");
                }
            }
        }

        String string = sb.toString();
        string = string.replaceAll("\n+", "\n");
        return string;
    }
}
