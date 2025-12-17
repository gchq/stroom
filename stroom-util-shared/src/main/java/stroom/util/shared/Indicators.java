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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A map of indicators to show in the XML editor.
 */
@JsonPropertyOrder({"errorCount", "uniqueErrorSet", "errorList"})
@JsonInclude(Include.NON_NULL)
public class Indicators {

    @JsonProperty
    private final Map<Severity, Integer> errorCount;
    @JsonProperty
    private final Set<StoredError> uniqueErrorSet;
    @JsonProperty
    private final List<StoredError> errorList;

    public Indicators() {
        errorCount = new ConcurrentHashMap<>();
        uniqueErrorSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
        errorList = Collections.synchronizedList(new ArrayList<>());
    }

    @JsonCreator
    public Indicators(@JsonProperty("errorCount") final Map<Severity, Integer> errorCount,
                      @JsonProperty("uniqueErrorSet") final Set<StoredError> uniqueErrorSet,
                      @JsonProperty("errorList") final List<StoredError> errorList) {
        this.errorCount = errorCount;
        this.uniqueErrorSet = uniqueErrorSet;
        this.errorList = errorList;
    }

    /**
     * Copying constructor.
     */
    public Indicators(final Indicators indicators) {
        this();
        addAll(indicators);
    }

    public static Indicators combine(final Indicators... indicatorsArr) {
        final Indicators result = new Indicators();
        if (indicatorsArr != null) {
            for (final Indicators indicators : indicatorsArr) {
                if (indicators != null) {
                    result.addAll(indicators);
                }
            }
        }
        return result;
    }

    public static Indicators combine(final Collection<Indicators> indicatorsCollection) {
        final Indicators result = new Indicators();
        if (indicatorsCollection != null) {
            for (final Indicators indicators : indicatorsCollection) {
                if (indicators != null) {
                    result.addAll(indicators);
                }
            }
        }
        return result;
    }

    public synchronized Map<Severity, Integer> getErrorCount() {
        return errorCount != null
                ? new HashMap<>(errorCount)
                : Collections.emptyMap();
    }

    public synchronized Set<StoredError> getUniqueErrorSet() {
        return uniqueErrorSet != null
                ? new HashSet<>(uniqueErrorSet)
                : Collections.emptySet();
    }

    public synchronized List<StoredError> getErrorList() {
        return errorList != null
                ? new ArrayList<>(errorList)
                : Collections.emptyList();
    }

    /**
     * Add all of the indicators from another map.
     */
    public synchronized void addAll(final Indicators indicators) {
        if (indicators != null) {
            // Merge
            for (final StoredError storedError : indicators.getErrorList()) {
                add(storedError);
            }
        }
    }

    public synchronized void addAll(final Collection<StoredError> storedErrors) {
        if (!NullSafe.isEmptyCollection(storedErrors)) {
            storedErrors.forEach(this::add);
        }
    }

    public synchronized void add(final StoredError storedError) {
        // Check to make sure we haven't seen this error before. If we have then
        // ignore it as we only want to store unique errors.
        if (uniqueErrorSet.add(storedError)) {
            errorList.add(storedError);
            errorCount.merge(storedError.getSeverity(), 1, Integer::sum);
        }
    }

    /**
     * @return A new {@link Indicators} instance containing only those {@link StoredError}s
     * matching the supplied types
     */
    public synchronized Indicators filter(final boolean includeLocationAgnostic,
                                          final ErrorType... includedErrorTypes) {

        if (includedErrorTypes == null || includedErrorTypes.length == 0) {
            return new Indicators(this);
        } else {
            final Set<ErrorType> includedErrorTypesSet = ErrorType.asSet(includedErrorTypes);
            final Predicate<StoredError> locationPredicate = includeLocationAgnostic
                    ? err -> true
                    : err -> err.getLocation() != null
                            && err.getLocation().getLineNo() > 0
                            && err.getLocation().getColNo() > 0;

            final List<StoredError> filteredErrors = NullSafe.list(errorList)
                    .stream()
                    .filter(storedError ->
                            includedErrorTypesSet.contains(storedError.getErrorType()))
                    .filter(locationPredicate)
                    .collect(Collectors.toList());

            if (errorList.size() == filteredErrors.size()) {
                return new Indicators(this);
            } else {
                final Indicators indicators = new Indicators();
                indicators.addAll(filteredErrors);
                return indicators;
            }
        }
    }

    /**
     * Clears the map.
     */
    public synchronized void clear() {
        uniqueErrorSet.clear();
        errorList.clear();
        errorCount.clear();
    }

    @JsonIgnore
    public synchronized Severity getMaxSeverity() {
        for (final Severity sev : Severity.SEVERITIES) {
            final Integer cnt = errorCount.get(sev);
            if (cnt != null && cnt > 0) {
                return sev;
            }
        }
        return null;
    }

    public synchronized int getCount(final Severity severity) {
        if (severity == null) {
            return 0;
        } else {
            return NullSafe.requireNonNullElse(errorCount.get(severity), 0);
        }
    }

    public synchronized void append(final StringBuilder sb) {
        for (final StoredError storedError : errorList) {
            storedError.append(sb);
            sb.append("\n");
        }
    }

    @Override
    public synchronized String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }

    @JsonIgnore
    public synchronized boolean isEmpty() {
        return errorList == null || errorList.isEmpty();
    }
}
