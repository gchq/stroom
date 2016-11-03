/*
 * Copyright 2016 Crown Copyright
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

    private Map<Severity, Set<StoredError>> errorMap;

    public Indicator() {
        // Default constructor necessary for GWT serialisation.
    }

    public Indicator(final Indicator indicator) {
        addAll(indicator);
    }

    public void addAll(final Indicator indicator) {
        if (indicator != null && indicator.errorMap != null && indicator.errorMap.size() > 0) {
            if (errorMap == null) {
                errorMap = new HashMap<Severity, Set<StoredError>>();
            }

            for (final Entry<Severity, Set<StoredError>> entry : indicator.errorMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue().size() > 0) {
                    Set<StoredError> messages = errorMap.get(entry.getKey());
                    if (messages == null) {
                        messages = new HashSet<StoredError>(entry.getValue().size());
                    }

                    if (entry.getValue() != null) {
                        messages.addAll(entry.getValue());
                    }
                }
            }
        }
    }

    public void add(final Severity severity, final StoredError message) {
        if (errorMap == null) {
            errorMap = new HashMap<Severity, Set<StoredError>>();
        }

        Set<StoredError> messages = errorMap.get(severity);
        if (messages == null) {
            messages = new HashSet<StoredError>();
            errorMap.put(severity, messages);
        }

        messages.add(message);
    }

    public Severity getMaxSeverity() {
        for (final Severity sev : Severity.SEVERITIES) {
            final Set<StoredError> messages = errorMap.get(sev);
            if (messages != null && messages.size() > 0) {
                return sev;
            }
        }
        return null;
    }

    /**
     * Gets some HTML to be used in the indicator popup on request.
     */
    public String getHTML() {
        String html = toString();
        html = html.replaceAll("&", "&amp;");
        html = html.replaceAll("<", "&lt;");
        html = html.replaceAll(">", "&gt;");
        html = html.replaceAll("\n", "<br/>");
        return html.trim();
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
