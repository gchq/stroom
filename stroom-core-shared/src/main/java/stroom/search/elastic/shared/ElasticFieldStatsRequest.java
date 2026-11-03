/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/// A request for statistics about one field of an Elasticsearch index doc.
@JsonPropertyOrder({
        "indexDocRef",
        "fieldName",
        "sampleSize",
        "fromMs",
        "toMs",
        "virtualGroups",
        "exact"})
@JsonInclude(Include.NON_NULL)
public final class ElasticFieldStatsRequest {

    @JsonProperty
    private final DocRef indexDocRef;
    @JsonProperty
    private final String fieldName;
    @JsonProperty
    private final Integer sampleSize;
    @JsonProperty
    private final Long fromMs;
    @JsonProperty
    private final Long toMs;
    @JsonProperty
    private final List<ElasticVirtualFieldGroup> virtualGroups;
    @JsonProperty
    private final Boolean exact;

    /// @param indexDocRef the index doc
    /// @param fieldName the full name of the field
    /// @param sampleSize the approximate number of documents to sample at random, or `null` for the configured
    ///     default; ignored when `exact` is `true`
    /// @param fromMs include only documents whose time field is at or after this time, in epoch milliseconds, or
    ///     `null` for no lower bound
    /// @param toMs include only documents whose time field is before this time, in epoch milliseconds, or `null`
    ///     for no upper bound
    /// @param virtualGroups for a virtual field, its groups as currently edited, which may not yet be saved;
    ///     otherwise `null`
    /// @param exact `true` to calculate the statistics across every document of the index pattern rather than a
    ///     random sample
    @JsonCreator
    public ElasticFieldStatsRequest(@JsonProperty("indexDocRef") final DocRef indexDocRef,
                                    @JsonProperty("fieldName") final String fieldName,
                                    @JsonProperty("sampleSize") final Integer sampleSize,
                                    @JsonProperty("fromMs") final Long fromMs,
                                    @JsonProperty("toMs") final Long toMs,
                                    @JsonProperty("virtualGroups") final List<ElasticVirtualFieldGroup> virtualGroups,
                                    @JsonProperty("exact") final Boolean exact) {
        this.indexDocRef = indexDocRef;
        this.fieldName = fieldName;
        this.sampleSize = sampleSize;
        this.fromMs = fromMs;
        this.toMs = toMs;
        this.virtualGroups = virtualGroups;
        this.exact = exact;
    }

    /// @return the index doc
    public DocRef getIndexDocRef() {
        return indexDocRef;
    }

    /// @return the full name of the field
    public String getFieldName() {
        return fieldName;
    }

    /// @return the approximate number of documents to sample at random, or `null` for the configured default;
    /// ignored when `exact` is `true`
    public Integer getSampleSize() {
        return sampleSize;
    }

    /// @return include only documents whose time field is at or after this time, in epoch milliseconds, or `null`
    /// for no lower bound
    public Long getFromMs() {
        return fromMs;
    }

    /// @return include only documents whose time field is before this time, in epoch milliseconds, or `null` for no
    /// upper bound
    public Long getToMs() {
        return toMs;
    }

    /// @return for a virtual field, its groups as currently edited, which may not yet be saved; otherwise `null`
    public List<ElasticVirtualFieldGroup> getVirtualGroups() {
        return virtualGroups;
    }

    /// @return `true` to calculate the statistics across every document of the index pattern rather than a random
    /// sample
    public Boolean getExact() {
        return exact;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ElasticFieldStatsRequest that = (ElasticFieldStatsRequest) o;
        return Objects.equals(indexDocRef, that.indexDocRef) &&
               Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(sampleSize, that.sampleSize) &&
               Objects.equals(fromMs, that.fromMs) &&
               Objects.equals(toMs, that.toMs) &&
               Objects.equals(virtualGroups, that.virtualGroups) &&
               Objects.equals(exact, that.exact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexDocRef, fieldName, sampleSize, fromMs, toMs, virtualGroups, exact);
    }

    @Override
    public String toString() {
        return "ElasticFieldStatsRequest{" +
               "indexDocRef=" + indexDocRef +
               ", fieldName=" + fieldName +
               ", sampleSize=" + sampleSize +
               ", fromMs=" + fromMs +
               ", toMs=" + toMs +
               ", virtualGroups=" + virtualGroups +
               ", exact=" + exact +
               '}';
    }
}
