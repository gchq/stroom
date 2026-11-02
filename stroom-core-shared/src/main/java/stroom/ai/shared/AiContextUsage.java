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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How much of the model's context window a chat would use if a question were asked now.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiContextUsage {

    /// The part the tool definitions offered to the model take.
    public static final String PART_TOOL_DEFINITIONS = "Tool definitions";
    /// The share of the context window, in percent, the tool definitions alone may take before the context meter
    /// says so.
    public static final int TOOL_DEFINITIONS_NOTE_PERCENT = 25;

    @JsonProperty
    private final int contextWindowTokens;
    @JsonProperty
    private final long usedTokens;
    @JsonProperty
    private final boolean estimated;
    @JsonProperty
    private final Map<String, Long> parts;
    @JsonProperty
    private final int compactAtPercent;
    @JsonProperty
    private final int shortenedParts;
    @JsonProperty
    private final long storedToolResultTokens;
    @JsonProperty
    private final Map<String, Long> largestTools;

    /**
     * @param contextWindowTokens The model's context window, or 0 if it is not known.
     * @param usedTokens          How many tokens the chat would use.
     * @param estimated           True if the figure is only an estimate, not yet checked against what the model
     *                            reported for this chat.
     * @param parts               Where the tokens go (e.g. "Chat history", "Tool definitions"), in order.
     * @param compactAtPercent    The percentage of the context window at which the chat is compacted
     *                            automatically, or 0 if not known.
     * @param shortenedParts      How many parts (tool results, tool call arguments or earlier steps) the run
     *                            answering the latest question shortened to fit the context window, so it sends
     *                            less than the chat holds, or 0.
     * @param storedToolResultTokens How many tokens the latest question's tool calls and results take as stored,
     *                               when the run sent fewer of them than that (it shortened them), or 0.
     * @param largestTools        The tools whose definitions took the most tokens in the chat's last model call,
     *                            largest first, with their tokens.
     */
    @JsonCreator
    public AiContextUsage(@JsonProperty("contextWindowTokens") final Integer contextWindowTokens,
                          @JsonProperty("usedTokens") final Long usedTokens,
                          @JsonProperty("estimated") final Boolean estimated,
                          @JsonProperty("parts") final Map<String, Long> parts,
                          @JsonProperty("compactAtPercent") final Integer compactAtPercent,
                          @JsonProperty("shortenedParts") final Integer shortenedParts,
                          @JsonProperty("storedToolResultTokens") final Long storedToolResultTokens,
                          @JsonProperty("largestTools") final Map<String, Long> largestTools) {
        this.contextWindowTokens = Objects.requireNonNullElse(contextWindowTokens, 0);
        this.usedTokens = Objects.requireNonNullElse(usedTokens, 0L);
        this.estimated = Objects.requireNonNullElse(estimated, true);
        this.parts = parts == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(parts);
        this.compactAtPercent = Objects.requireNonNullElse(compactAtPercent, 0);
        this.shortenedParts = Objects.requireNonNullElse(shortenedParts, 0);
        this.storedToolResultTokens = Objects.requireNonNullElse(storedToolResultTokens, 0L);
        this.largestTools = largestTools == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(largestTools);
    }

    /**
     * @return The model's context window, or 0 if it is not known.
     */
    public int getContextWindowTokens() {
        return contextWindowTokens;
    }

    public long getUsedTokens() {
        return usedTokens;
    }

    /**
     * @return True if the figure is only an estimate, not yet checked against what the model reported.
     */
    public boolean isEstimated() {
        return estimated;
    }

    /**
     * @return Where the tokens go, in order.
     */
    public Map<String, Long> getParts() {
        return parts;
    }

    /**
     * @return The percentage of the context window at which the chat is compacted automatically, or 0 if not
     * known.
     */
    public int getCompactAtPercent() {
        return compactAtPercent;
    }

    /**
     * @return How many parts the run answering the latest question shortened to fit the context window, or 0.
     */
    public int getShortenedParts() {
        return shortenedParts;
    }

    /**
     * @return How many tokens the latest question's tool calls and results take as stored, when the run sent
     * fewer of them than that, or 0.
     */
    public long getStoredToolResultTokens() {
        return storedToolResultTokens;
    }

    /**
     * @return The tools whose definitions took the most tokens in the chat's last model call, largest first,
     * with their tokens.
     */
    public Map<String, Long> getLargestTools() {
        return largestTools;
    }

    /**
     * @return The percentage of the context window used, or -1 if the window is not known.
     */
    @JsonIgnore
    public int getPercentUsed() {
        return contextWindowTokens > 0
                ? (int) Math.min(999, (usedTokens * 100) / contextWindowTokens)
                : -1;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiContextUsage that = (AiContextUsage) o;
        return contextWindowTokens == that.contextWindowTokens
               && usedTokens == that.usedTokens
               && estimated == that.estimated
               && Objects.equals(parts, that.parts)
               && compactAtPercent == that.compactAtPercent
               && shortenedParts == that.shortenedParts
               && storedToolResultTokens == that.storedToolResultTokens
               && Objects.equals(largestTools, that.largestTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextWindowTokens, usedTokens, estimated, parts, compactAtPercent, shortenedParts,
                storedToolResultTokens, largestTools);
    }

    @Override
    public String toString() {
        return "AiContextUsage{" +
               "contextWindowTokens=" + contextWindowTokens +
               ", usedTokens=" + usedTokens +
               ", estimated=" + estimated +
               ", parts=" + parts +
               ", compactAtPercent=" + compactAtPercent +
               ", shortenedParts=" + shortenedParts +
               ", storedToolResultTokens=" + storedToolResultTokens +
               ", largestTools=" + largestTools +
               '}';
    }
}
