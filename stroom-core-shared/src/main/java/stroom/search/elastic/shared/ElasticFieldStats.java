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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/// Statistics about one field of an Elasticsearch index doc, calculated across every matching document or estimated
/// from a random sample of them.
///
/// Which statistics are present depends on the field's type. Counts are for all matching documents: exact, or estimated
/// from the sample. Values are rendered as text, so dates and numbers can be shown as Elasticsearch formats them.
@JsonPropertyOrder({
        "fieldName",
        "exact",
        "sampleSize",
        "maxSampleSize",
        "totalDocs",
        "sampledDocs",
        "docsWithValue",
        "distinctCount",
        "topValues",
        "min",
        "max",
        "mean",
        "median",
        "histogram",
        "message"})
@JsonInclude(Include.NON_NULL)
public final class ElasticFieldStats {

    @JsonProperty
    private final String fieldName;
    @JsonProperty
    private final Boolean exact;
    @JsonProperty
    private final Integer sampleSize;
    @JsonProperty
    private final Integer maxSampleSize;
    @JsonProperty
    private final Long totalDocs;
    @JsonProperty
    private final Long sampledDocs;
    @JsonProperty
    private final Long docsWithValue;
    @JsonProperty
    private final Long distinctCount;
    @JsonProperty
    private final List<ElasticFieldValueCount> topValues;
    @JsonProperty
    private final String min;
    @JsonProperty
    private final String max;
    @JsonProperty
    private final Double mean;
    @JsonProperty
    private final Double median;
    @JsonProperty
    private final List<ElasticFieldValueCount> histogram;
    @JsonProperty
    private final String message;

    /// @param fieldName the full name of the field
    /// @param exact `true` if the statistics were calculated across every matching document, `false` if estimated
    ///     from a random sample
    /// @param sampleSize the approximate number of documents asked to be sampled
    /// @param maxSampleSize the largest sample size that may be requested
    /// @param totalDocs the number of documents matching the time range
    /// @param sampledDocs the approximate number of documents the statistics were calculated from
    /// @param docsWithValue the number of matching documents holding a value for the field
    /// @param distinctCount the approximate number of distinct values, or `null` if not applicable
    /// @param topValues the most common values with their counts, or `null` if not applicable
    /// @param min the smallest value, or `null` if not applicable
    /// @param max the largest value, or `null` if not applicable
    /// @param mean the mean of numeric values, or `null` if not applicable
    /// @param median the median of numeric values, or `null` if not applicable
    /// @param histogram the distribution of numeric or date values as ordered buckets, keyed by each bucket's lower
    ///     bound, or `null` if not applicable
    /// @param message an explanation when no statistics can be given for the field, otherwise `null`
    @JsonCreator
    public ElasticFieldStats(@JsonProperty("fieldName") final String fieldName,
                             @JsonProperty("exact") final Boolean exact,
                             @JsonProperty("sampleSize") final Integer sampleSize,
                             @JsonProperty("maxSampleSize") final Integer maxSampleSize,
                             @JsonProperty("totalDocs") final Long totalDocs,
                             @JsonProperty("sampledDocs") final Long sampledDocs,
                             @JsonProperty("docsWithValue") final Long docsWithValue,
                             @JsonProperty("distinctCount") final Long distinctCount,
                             @JsonProperty("topValues") final List<ElasticFieldValueCount> topValues,
                             @JsonProperty("min") final String min,
                             @JsonProperty("max") final String max,
                             @JsonProperty("mean") final Double mean,
                             @JsonProperty("median") final Double median,
                             @JsonProperty("histogram") final List<ElasticFieldValueCount> histogram,
                             @JsonProperty("message") final String message) {
        this.fieldName = fieldName;
        this.exact = exact;
        this.sampleSize = sampleSize;
        this.maxSampleSize = maxSampleSize;
        this.totalDocs = totalDocs;
        this.sampledDocs = sampledDocs;
        this.docsWithValue = docsWithValue;
        this.distinctCount = distinctCount;
        this.topValues = topValues;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.median = median;
        this.histogram = histogram;
        this.message = message;
    }

    /// @return the full name of the field
    public String getFieldName() {
        return fieldName;
    }

    /// @return `true` if the statistics were calculated across every matching document, `false` if estimated from a
    /// random sample
    public Boolean getExact() {
        return exact;
    }

    /// @return the approximate number of documents asked to be sampled
    public Integer getSampleSize() {
        return sampleSize;
    }

    /// @return the largest sample size that may be requested
    public Integer getMaxSampleSize() {
        return maxSampleSize;
    }

    /// @return the number of documents matching the time range
    public Long getTotalDocs() {
        return totalDocs;
    }

    /// @return the approximate number of documents the statistics were calculated from
    public Long getSampledDocs() {
        return sampledDocs;
    }

    /// @return the number of matching documents holding a value for the field
    public Long getDocsWithValue() {
        return docsWithValue;
    }

    /// @return the approximate number of distinct values, or `null` if not applicable
    public Long getDistinctCount() {
        return distinctCount;
    }

    /// @return the most common values with their counts, or `null` if not applicable
    public List<ElasticFieldValueCount> getTopValues() {
        return topValues;
    }

    /// @return the smallest value, or `null` if not applicable
    public String getMin() {
        return min;
    }

    /// @return the largest value, or `null` if not applicable
    public String getMax() {
        return max;
    }

    /// @return the mean of numeric values, or `null` if not applicable
    public Double getMean() {
        return mean;
    }

    /// @return the median of numeric values, or `null` if not applicable
    public Double getMedian() {
        return median;
    }

    /// @return the distribution of numeric or date values as ordered buckets, keyed by each bucket's lower bound,
    /// or `null` if not applicable
    public List<ElasticFieldValueCount> getHistogram() {
        return histogram;
    }

    /// @return an explanation when no statistics can be given for the field, otherwise `null`
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ElasticFieldStats that = (ElasticFieldStats) o;
        return Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(exact, that.exact) &&
               Objects.equals(sampleSize, that.sampleSize) &&
               Objects.equals(maxSampleSize, that.maxSampleSize) &&
               Objects.equals(totalDocs, that.totalDocs) &&
               Objects.equals(sampledDocs, that.sampledDocs) &&
               Objects.equals(docsWithValue, that.docsWithValue) &&
               Objects.equals(distinctCount, that.distinctCount) &&
               Objects.equals(topValues, that.topValues) &&
               Objects.equals(min, that.min) &&
               Objects.equals(max, that.max) &&
               Objects.equals(mean, that.mean) &&
               Objects.equals(median, that.median) &&
               Objects.equals(histogram, that.histogram) &&
               Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, exact, sampleSize, maxSampleSize, totalDocs, sampledDocs, docsWithValue,
                distinctCount, topValues, min, max, mean, median, histogram, message);
    }

    @Override
    public String toString() {
        return "ElasticFieldStats{" +
               "fieldName=" + fieldName +
               ", exact=" + exact +
               ", sampleSize=" + sampleSize +
               ", maxSampleSize=" + maxSampleSize +
               ", totalDocs=" + totalDocs +
               ", sampledDocs=" + sampledDocs +
               ", docsWithValue=" + docsWithValue +
               ", distinctCount=" + distinctCount +
               ", topValues=" + topValues +
               ", min=" + min +
               ", max=" + max +
               ", mean=" + mean +
               ", median=" + median +
               ", histogram=" + histogram +
               ", message=" + message +
               '}';
    }
}
