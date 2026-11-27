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

package stroom.ai.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/// How much of a stream the agent's tools read as its sample (profile_sample, the translation checks, survey_feed
/// and the other tools given stream_ids): a part is read as a stream of whole events up to its share of characters,
/// an event too long to sample left out rather than cut, and a stream's parts are chosen spread through it.
@JsonPropertyOrder(alphabetic = true)
public class AiSamplingConfig extends AbstractConfig implements IsStroomConfig {

    /// The most characters of a stream's sample unless configured.
    public static final int DEFAULT_MAX_CHARS_PER_STREAM = 2_000_000;
    /// The longest event sampled unless configured.
    public static final int DEFAULT_MAX_EVENT_CHARS = 100_000;
    /// The fewest characters a stream's sample may be set to.
    static final int MIN_MAX_CHARS_PER_STREAM = 10_000;
    /// The shortest longest event that may be set.
    static final int MIN_MAX_EVENT_CHARS = 1_000;

    private final int maxCharsPerStream;
    private final int maxEventChars;

    public AiSamplingConfig() {
        maxCharsPerStream = DEFAULT_MAX_CHARS_PER_STREAM;
        maxEventChars = DEFAULT_MAX_EVENT_CHARS;
    }

    @JsonCreator
    public AiSamplingConfig(@JsonProperty("maxCharsPerStream") final Integer maxCharsPerStream,
                            @JsonProperty("maxEventChars") final Integer maxEventChars) {
        // Never below the least that still samples something: sampling can't be turned off.
        this.maxCharsPerStream = Math.max(MIN_MAX_CHARS_PER_STREAM,
                Objects.requireNonNullElse(maxCharsPerStream, DEFAULT_MAX_CHARS_PER_STREAM));
        this.maxEventChars = Math.max(MIN_MAX_EVENT_CHARS,
                Objects.requireNonNullElse(maxEventChars, DEFAULT_MAX_EVENT_CHARS));
    }

    /// @return The most characters of whole events read from one stream in a call, shared by the parts read.
    @Min(MIN_MAX_CHARS_PER_STREAM)
    @JsonPropertyDescription("The most characters of a stream that the agent's tools read in one call as its "
                             + "sample (profile_sample, the translation checks and the other tools given "
                             + "stream_ids), shared by the parts read: a stream of many parts has up to 20 of them "
                             + "read, spread through it from its first to its last, each at least one event of "
                             + "maxEventChars however small this is. Only whole events are kept. survey_feed reads "
                             + "this many characters of each part of a sample stream (and of a feed's last "
                             + "streams). At least 10,000.")
    @JsonProperty("maxCharsPerStream")
    public int getMaxCharsPerStream() {
        return maxCharsPerStream;
    }

    /// @return The longest event kept in a sample: a longer one is left out, never cut.
    @Min(MIN_MAX_EVENT_CHARS)
    @JsonPropertyDescription("The longest event, in characters, kept in a stream's sample. A longer event is read "
                             + "through and left out of the sample rather than cut, and the tools say so (with its "
                             + "length and its longest string value, so a value Stroom's JSONParser would cut is "
                             + "still reported). At least 1,000.")
    @JsonProperty("maxEventChars")
    public int getMaxEventChars() {
        return maxEventChars;
    }

    @Override
    public String toString() {
        return "AiSamplingConfig{" +
               "maxCharsPerStream=" + maxCharsPerStream +
               ", maxEventChars=" + maxEventChars +
               '}';
    }
}
