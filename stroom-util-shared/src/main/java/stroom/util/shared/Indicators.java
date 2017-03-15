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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A map of indicators to show in the XML editor.
 */
public class Indicators implements Serializable {
    private static final long serialVersionUID = 1445199511079506470L;

    private Map<Severity, Integer> errorCount = new HashMap<>();
    private Set<StoredError> uniqueErrorSet = new HashSet<>();
    private List<StoredError> errorList = new ArrayList<>();
    private Map<Integer, Indicator> map;

    public Indicators() {
        // Default constructor necessary for GWT serialisation.
    }

    /**
     * Copying constructor.
     */
    public Indicators(final Indicators indicators) {
        addAll(indicators);
    }

    /**
     * Add all of the indicators from another map.
     */
    public void addAll(final Indicators indicators) {
        if (indicators != null) {
            // Merge
            for (final StoredError storedError : indicators.errorList) {
                add(storedError);
            }
        }
    }

    public void add(final StoredError storedError) {
        // Check to make sure we haven't seen this error before. If we have then
        // ignore it as we only want to store unique errors.
        if (uniqueErrorSet.add(storedError)) {
            errorList.add(storedError);

            final Integer count = errorCount.get(storedError.getSeverity());
            if (count == null) {
                errorCount.put(storedError.getSeverity(), 1);
            } else {
                errorCount.put(storedError.getSeverity(), count + 1);
            }
        }
    }

    /**
     * Clears the map.
     */
    public void clear() {
        uniqueErrorSet.clear();
        errorList.clear();
        errorCount.clear();
    }

    public Severity getMaxSeverity() {
        for (final Severity sev : Severity.SEVERITIES) {
            final Integer c = errorCount.get(sev);
            if (c != null && c > 0) {
                return sev;
            }
        }
        return null;
    }

    /**
     * Gets a summary of the counts of warnings, errors and fatal errors.
     *
     * @return A summary of the counts of warnings, errors and fatal errors.
     */
    public String getSummaryHTML() {
        final StringBuilder html = new StringBuilder();
        for (final Severity severity : Severity.SEVERITIES) {
            final Integer count = errorCount.get(severity);
            if (count != null && count > 0) {
                html.append(severity.getDisplayValue());
                html.append(": ");
                html.append(count);
                html.append("<br/>");
            }
        }

        return html.toString();
    }

    public Collection<Integer> getLineNumbers() {
        return getMap().keySet();
    }

    public Indicator getIndicator(final int lineNo) {
        return getMap().get(lineNo);
    }

    private Map<Integer, Indicator> getMap() {
        if (map == null) {
            map = new HashMap<>();
            for (final StoredError storedError : errorList) {
                int lineNo = 1;
                if (storedError.getLocation() != null) {
                    lineNo = storedError.getLocation().getLineNo();
                }
                if (lineNo <= 0) {
                    lineNo = 1;
                }

                map.computeIfAbsent(lineNo, k -> new Indicator()).add(storedError.getSeverity(), storedError);
            }
        }

        return map;
    }

    public void append(final StringBuilder sb) {
        for (final StoredError storedError : errorList) {
            storedError.append(sb);
            sb.append("\n");
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }
}
