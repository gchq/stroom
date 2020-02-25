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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A map of indicators to show in the XML editor.
 */
@JsonPropertyOrder({"errorCount", "uniqueErrorSet", "errorList"})
@JsonInclude(Include.NON_DEFAULT)
public class Indicators {
    @JsonProperty
    private final Map<Severity, Integer> errorCount;
    @JsonProperty
    private final Set<StoredError> uniqueErrorSet;
    @JsonProperty
    private final List<StoredError> errorList;

    public Indicators() {
        errorCount = new HashMap<>();
        uniqueErrorSet = new HashSet<>();
        errorList = new ArrayList<>();
    }

    @JsonCreator
    public Indicators(@JsonProperty("errorCount") final Map<Severity, Integer> errorCount,
                      @JsonProperty("uniqueErrorSet") final Set<StoredError> uniqueErrorSet,
                      @JsonProperty("errorList") final List<StoredError> errorList) {
        if (errorCount != null) {
            this.errorCount = errorCount;
        } else {
            this.errorCount = new HashMap<>();
        }
        if (uniqueErrorSet != null) {
            this.uniqueErrorSet = uniqueErrorSet;
        } else {
            this.uniqueErrorSet = new HashSet<>();
        }
        if (errorList != null) {
            this.errorList = errorList;
        } else {
            this.errorList = new ArrayList<>();
        }
    }

    /**
     * Copying constructor.
     */
    public Indicators(final Indicators indicators) {
        errorCount = new HashMap<>();
        uniqueErrorSet = new HashSet<>();
        errorList = new ArrayList<>();
        addAll(indicators);
    }

    public Map<Severity, Integer> getErrorCount() {
        return errorCount;
    }

    public Set<StoredError> getUniqueErrorSet() {
        return uniqueErrorSet;
    }

    public List<StoredError> getErrorList() {
        return errorList;
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

    @JsonIgnore
    public Severity getMaxSeverity() {
        for (final Severity sev : Severity.SEVERITIES) {
            final Integer c = errorCount.get(sev);
            if (c != null && c > 0) {
                return sev;
            }
        }
        return null;
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
