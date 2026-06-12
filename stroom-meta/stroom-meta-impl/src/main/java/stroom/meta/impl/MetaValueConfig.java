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

package stroom.meta.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class MetaValueConfig extends AbstractConfig implements IsStroomConfig {

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The age of streams that we store meta data in the database for. " +
            "In ISO-8601 duration format, e.g. 'P1DT12H'")
    private final StroomDuration deleteAge;

    @JsonProperty
    @JsonPropertyDescription("How many stream attributes we want to try and delete in a single batch.")
    private final int deleteBatchSize;

    @JsonProperty
    @JsonPropertyDescription("The number of stream attributes to queue before flushing to the database. " +
            "Only applicable if property 'addAsync' is true.")
    private final int flushBatchSize;

    @JsonProperty
    @JsonPropertyDescription("If true, stream attributes will be queued in memory until the queue " +
            "reaches 'flushBatchSize'. If false, stream attributes will be written to the database " +
            "immediately and synchronously.")
    // TODO 01/12/2021 AT: Make final
    private boolean addAsync;

    public MetaValueConfig() {
        deleteAge = StroomDuration.ofDays(30);
        deleteBatchSize = 500;
        flushBatchSize = 500;
        addAsync = true;
    }

    @JsonCreator
    public MetaValueConfig(@JsonProperty("deleteAge") final StroomDuration deleteAge,
                           @JsonProperty("deleteBatchSize") final int deleteBatchSize,
                           @JsonProperty("flushBatchSize") final int flushBatchSize,
                           @JsonProperty("addAsync") final boolean addAsync) {
        this.deleteAge = deleteAge;
        this.deleteBatchSize = deleteBatchSize;
        this.flushBatchSize = flushBatchSize;
        this.addAsync = addAsync;
    }

    public StroomDuration getDeleteAge() {
        return deleteAge;
    }

    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    public boolean isAddAsync() {
        return addAsync;
    }

    @Deprecated(forRemoval = true)
    public void setAddAsync(final boolean addAsync) {
        this.addAsync = addAsync;
    }

    public MetaValueConfig withAddAsync(final boolean addAsync) {
        return new MetaValueConfig(deleteAge, deleteBatchSize, flushBatchSize, addAsync);
    }

    @Override
    public String toString() {
        return "MetaValueConfig{" +
                "deleteAge=" + deleteAge +
                ", deleteBatchSize=" + deleteBatchSize +
                ", flushBatchSize=" + flushBatchSize +
                ", addAsync=" + addAsync +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MetaValueConfig that = (MetaValueConfig) o;
        return deleteBatchSize == that.deleteBatchSize &&
                flushBatchSize == that.flushBatchSize &&
                addAsync == that.addAsync &&
                Objects.equals(deleteAge, that.deleteAge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deleteAge, deleteBatchSize, flushBatchSize, addAsync);
    }
}
