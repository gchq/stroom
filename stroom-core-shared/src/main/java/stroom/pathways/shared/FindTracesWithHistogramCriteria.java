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

package stroom.pathways.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Asks for a page of traces and a histogram of the same window in one request, so that both are read
 * from one copy of each archive bucket and one resolved time window. {@code bucketCount} is the number
 * of equal time buckets wanted across the window; the histogram comes back marked unavailable when the
 * window is unbounded or wider than the store allows (see {@link TraceHistogram#isAvailable()}).
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FindTracesWithHistogramCriteria {

    @JsonProperty
    private final FindTraceCriteria criteria;
    @JsonProperty
    private final int bucketCount;

    @JsonCreator
    public FindTracesWithHistogramCriteria(@JsonProperty("criteria") final FindTraceCriteria criteria,
                                           @JsonProperty("bucketCount") final int bucketCount) {
        this.criteria = criteria;
        this.bucketCount = bucketCount;
    }

    public FindTraceCriteria getCriteria() {
        return criteria;
    }

    public int getBucketCount() {
        return bucketCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FindTracesWithHistogramCriteria that = (FindTracesWithHistogramCriteria) o;
        return bucketCount == that.bucketCount && Objects.equals(criteria, that.criteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(criteria, bucketCount);
    }

    @Override
    public String toString() {
        return "FindTracesWithHistogramCriteria{" +
               "criteria=" + criteria +
               ", bucketCount=" + bucketCount +
               '}';
    }
}
