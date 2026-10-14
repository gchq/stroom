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

import java.util.Objects;

/// How 'Ask Stroom AI' chats are compacted to keep them within the model's context window.
@JsonPropertyOrder(alphabetic = true)
public class AiCompactionConfig extends AbstractConfig implements IsStroomConfig {

    static final int DEFAULT_THRESHOLD_PERCENT = 80;
    static final int DEFAULT_TURNS_KEPT_IN_FULL = 2;
    static final int DEFAULT_DIGEST_MAX_RESULT_ROWS = 20;
    static final int DEFAULT_REPLY_RESERVE_TOKENS = 4_096;

    private final int thresholdPercent;
    private final int turnsKeptInFull;
    private final int digestMaxResultRows;
    private final int replyReserveTokens;

    public AiCompactionConfig() {
        thresholdPercent = DEFAULT_THRESHOLD_PERCENT;
        turnsKeptInFull = DEFAULT_TURNS_KEPT_IN_FULL;
        digestMaxResultRows = DEFAULT_DIGEST_MAX_RESULT_ROWS;
        replyReserveTokens = DEFAULT_REPLY_RESERVE_TOKENS;
    }

    @JsonCreator
    public AiCompactionConfig(@JsonProperty("thresholdPercent") final Integer thresholdPercent,
                              @JsonProperty("turnsKeptInFull") final Integer turnsKeptInFull,
                              @JsonProperty("digestMaxResultRows") final Integer digestMaxResultRows,
                              @JsonProperty("replyReserveTokens") final Integer replyReserveTokens) {
        this.thresholdPercent = Objects.requireNonNullElse(thresholdPercent, DEFAULT_THRESHOLD_PERCENT);
        this.turnsKeptInFull = Objects.requireNonNullElse(turnsKeptInFull, DEFAULT_TURNS_KEPT_IN_FULL);
        this.digestMaxResultRows = Objects.requireNonNullElse(digestMaxResultRows, DEFAULT_DIGEST_MAX_RESULT_ROWS);
        this.replyReserveTokens = Objects.requireNonNullElse(replyReserveTokens, DEFAULT_REPLY_RESERVE_TOKENS);
    }

    @JsonPropertyDescription("How full (as a percentage of the model's context window, less the reply reserve) " +
                             "a chat may get before older turns are compacted. Only used for models whose " +
                             "document gives their context window size.")
    @JsonProperty("thresholdPercent")
    public int getThresholdPercent() {
        return thresholdPercent;
    }

    @JsonPropertyDescription("How many of the latest finished turns (question and answer) are always kept in " +
                             "full when a chat is compacted. Older turns are replaced by short digests.")
    @JsonProperty("turnsKeptInFull")
    public int getTurnsKeptInFull() {
        return turnsKeptInFull;
    }

    @JsonPropertyDescription("Tool results of up to this many lines are copied exactly into a compacted " +
                             "turn's digest. Larger results are referred to by where they are stored.")
    @JsonProperty("digestMaxResultRows")
    public int getDigestMaxResultRows() {
        return digestMaxResultRows;
    }

    @JsonPropertyDescription("How many tokens of the model's context window are kept free for its reply.")
    @JsonProperty("replyReserveTokens")
    public int getReplyReserveTokens() {
        return replyReserveTokens;
    }

    @Override
    public String toString() {
        return "AiCompactionConfig{" +
               "thresholdPercent=" + thresholdPercent +
               ", turnsKeptInFull=" + turnsKeptInFull +
               ", digestMaxResultRows=" + digestMaxResultRows +
               ", replyReserveTokens=" + replyReserveTokens +
               '}';
    }
}
