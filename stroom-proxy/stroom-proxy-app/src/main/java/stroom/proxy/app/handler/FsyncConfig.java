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

package stroom.proxy.app.handler;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.io.FsyncMode;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Controls whether data is forced to durable storage (`fsync`) as it passes through each
/// phase of the proxy pipeline.
///
/// Without this, data that has been written and acknowledged may still only be in the
/// operating system's page cache and can be lost if the machine loses power.
///
/// Every phase after receipt is carrying data the sender has already been told is safe, so all
/// of them matter. They are configured separately because what they cost differs markedly, not
/// because some are optional:
///
/// * [#getReceivingMode()] is the expensive one, as it forces the whole of the received payload to
///   disk before a receipt response is returned to the sender. This is what makes the receipt
///   honest; a crash before the response is sent costs nothing, as the sender simply retries.
/// * The queue settings are close to free. They force the directory entry created by the atomic
///   move that commits an item into the queue, not the item's contents, so they are a small
///   metadata flush rather than a flush of the data itself.
///
/// The practical consequence is that turning off a queue setting saves very little, whereas
/// turning off [#getReceivingMode()] buys back real throughput at the cost of the receipt guarantee.
/// That is the one to consider changing if receipt latency matters more than durability.
///
/// Note that [#getReceivingMode()] also covers data that is rewritten after receipt, by the zip
/// splitter and the aggregator. Those phases write new files and then delete the originals that
/// were forced on receipt, so without forcing the rewritten output the receipt guarantee would be
/// lost partway through the pipeline.
@JsonPropertyOrder(alphabetic = true)
public class FsyncConfig extends AbstractConfig implements IsProxyConfig {

    public static final FsyncMode DEFAULT_RECEIVING_MODE = FsyncMode.ENABLED;
    public static final FsyncMode DEFAULT_ZIP_SPLITTING_INPUT_QUEUE_MODE = FsyncMode.ENABLED;
    public static final FsyncMode DEFAULT_PRE_AGGREGATE_INPUT_QUEUE_MODE = FsyncMode.ENABLED;
    public static final FsyncMode DEFAULT_AGGREGATE_INPUT_QUEUE_MODE = FsyncMode.ENABLED;
    public static final FsyncMode DEFAULT_FORWARDING_INPUT_QUEUE_MODE = FsyncMode.ENABLED;

    private final FsyncMode receivingMode;
    private final FsyncMode zipSplittingInputQueueMode;
    private final FsyncMode preAggregateInputQueueMode;
    private final FsyncMode aggregateInputQueueMode;
    private final FsyncMode forwardingInputQueueMode;

    public FsyncConfig() {
        receivingMode = DEFAULT_RECEIVING_MODE;
        zipSplittingInputQueueMode = DEFAULT_ZIP_SPLITTING_INPUT_QUEUE_MODE;
        preAggregateInputQueueMode = DEFAULT_PRE_AGGREGATE_INPUT_QUEUE_MODE;
        aggregateInputQueueMode = DEFAULT_AGGREGATE_INPUT_QUEUE_MODE;
        forwardingInputQueueMode = DEFAULT_FORWARDING_INPUT_QUEUE_MODE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FsyncConfig(
            @JsonProperty("receivingMode") final FsyncMode receivingMode,
            @JsonProperty("zipSplittingInputQueueMode") final FsyncMode zipSplittingInputQueueMode,
            @JsonProperty("preAggregateInputQueueMode") final FsyncMode preAggregateInputQueueMode,
            @JsonProperty("aggregateInputQueueMode") final FsyncMode aggregateInputQueueMode,
            @JsonProperty("forwardingInputQueueMode") final FsyncMode forwardingInputQueueMode) {

        this.receivingMode = Objects.requireNonNullElse(
                receivingMode, DEFAULT_RECEIVING_MODE);
        this.zipSplittingInputQueueMode = Objects.requireNonNullElse(
                zipSplittingInputQueueMode, DEFAULT_ZIP_SPLITTING_INPUT_QUEUE_MODE);
        this.preAggregateInputQueueMode = Objects.requireNonNullElse(
                preAggregateInputQueueMode, DEFAULT_PRE_AGGREGATE_INPUT_QUEUE_MODE);
        this.aggregateInputQueueMode = Objects.requireNonNullElse(
                aggregateInputQueueMode, DEFAULT_AGGREGATE_INPUT_QUEUE_MODE);
        this.forwardingInputQueueMode = Objects.requireNonNullElse(
                forwardingInputQueueMode, DEFAULT_FORWARDING_INPUT_QUEUE_MODE);
    }

    @JsonPropertyDescription("Controls whether received files, directories, or both are forced to " +
                             "durable storage before a receipt response is returned to the sender, " +
                             "as is any data rewritten from it by the zip splitter or the aggregator.")
    @RequiresProxyRestart
    @JsonProperty
    public FsyncMode getReceivingMode() {
        return receivingMode;
    }

    @JsonPropertyDescription("Controls whether entries added to the zip splitting input queue have " +
                             "their directories forced to durable storage.")
    @RequiresProxyRestart
    @JsonProperty
    public FsyncMode getZipSplittingInputQueueMode() {
        return zipSplittingInputQueueMode;
    }

    @JsonPropertyDescription("Controls whether entries added to the pre-aggregate input queue have " +
                             "their directories forced to durable storage.")
    @RequiresProxyRestart
    @JsonProperty
    public FsyncMode getPreAggregateInputQueueMode() {
        return preAggregateInputQueueMode;
    }

    @JsonPropertyDescription("Controls whether entries added to the aggregate input queue have " +
                             "their directories forced to durable storage.")
    @RequiresProxyRestart
    @JsonProperty
    public FsyncMode getAggregateInputQueueMode() {
        return aggregateInputQueueMode;
    }

    @JsonPropertyDescription("Controls whether entries added to the forwarding input queue, and to " +
                             "the forward and retry queues of each forward destination, have their " +
                             "directories forced to durable storage.")
    @RequiresProxyRestart
    @JsonProperty
    public FsyncMode getForwardingInputQueueMode() {
        return forwardingInputQueueMode;
    }

    @Override
    public String toString() {
        return "FsyncConfig{" +
               "receivingMode=" + receivingMode +
               ", zipSplittingInputQueueMode=" + zipSplittingInputQueueMode +
               ", preAggregateInputQueueMode=" + preAggregateInputQueueMode +
               ", aggregateInputQueueMode=" + aggregateInputQueueMode +
               ", forwardingInputQueueMode=" + forwardingInputQueueMode +
               '}';
    }
}
