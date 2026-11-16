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
import stroom.util.shared.validation.ValidationSeverity;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/// How 'Ask Stroom AI' chats are compacted to keep them within the model's context window.
@JsonPropertyOrder(alphabetic = true)
public class AiCompactionConfig extends AbstractConfig implements IsStroomConfig {

    static final int DEFAULT_THRESHOLD_PERCENT = 80;
    static final int DEFAULT_TURNS_KEPT_IN_FULL = 2;
    static final int DEFAULT_DIGEST_MAX_RESULT_ROWS = 20;
    static final int DEFAULT_REPLY_RESERVE_TOKENS = 4_096;
    static final StroomDuration DEFAULT_IDLE_AFTER = StroomDuration.ofMinutes(10);
    static final int DEFAULT_IDLE_THRESHOLD_PERCENT = 60;

    private final int thresholdPercent;
    private final int turnsKeptInFull;
    private final int digestMaxResultRows;
    private final int replyReserveTokens;
    private final StroomDuration idleAfter;
    private final int idleThresholdPercent;

    public AiCompactionConfig() {
        thresholdPercent = DEFAULT_THRESHOLD_PERCENT;
        turnsKeptInFull = DEFAULT_TURNS_KEPT_IN_FULL;
        digestMaxResultRows = DEFAULT_DIGEST_MAX_RESULT_ROWS;
        replyReserveTokens = DEFAULT_REPLY_RESERVE_TOKENS;
        idleAfter = DEFAULT_IDLE_AFTER;
        idleThresholdPercent = DEFAULT_IDLE_THRESHOLD_PERCENT;
    }

    @JsonCreator
    public AiCompactionConfig(@JsonProperty("thresholdPercent") final Integer thresholdPercent,
                              @JsonProperty("turnsKeptInFull") final Integer turnsKeptInFull,
                              @JsonProperty("digestMaxResultRows") final Integer digestMaxResultRows,
                              @JsonProperty("replyReserveTokens") final Integer replyReserveTokens,
                              @JsonProperty("idleAfter") final StroomDuration idleAfter,
                              @JsonProperty("idleThresholdPercent") final Integer idleThresholdPercent) {
        this.thresholdPercent = Objects.requireNonNullElse(thresholdPercent, DEFAULT_THRESHOLD_PERCENT);
        this.turnsKeptInFull = Objects.requireNonNullElse(turnsKeptInFull, DEFAULT_TURNS_KEPT_IN_FULL);
        this.digestMaxResultRows = Objects.requireNonNullElse(digestMaxResultRows, DEFAULT_DIGEST_MAX_RESULT_ROWS);
        this.replyReserveTokens = Objects.requireNonNullElse(replyReserveTokens, DEFAULT_REPLY_RESERVE_TOKENS);
        // Null is kept, as it turns idle compaction off.
        this.idleAfter = idleAfter;
        this.idleThresholdPercent = Objects.requireNonNullElse(idleThresholdPercent,
                DEFAULT_IDLE_THRESHOLD_PERCENT);
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

    @JsonPropertyDescription("How long after its last message a chat counts as idle, so that the 'AI Chat Idle " +
                             "Compaction' job may compact it before the user comes back (see " +
                             "idleThresholdPercent). Empty or zero turns compacting idle chats off.")
    @JsonProperty("idleAfter")
    public StroomDuration getIdleAfter() {
        return idleAfter;
    }

    @Min(1)
    @Max(100)
    @JsonPropertyDescription("How full (as a percentage of the model's context window, less the reply reserve) " +
                             "an idle chat must be for it to be compacted while idle. It is meant to be below " +
                             "thresholdPercent, so that the user's next question need not wait for a compaction. " +
                             "Idle chats under it are left alone. If it is set at or above thresholdPercent, " +
                             "thresholdPercent is used instead: an idle chat is then compacted only once it is " +
                             "as full as a question would compact it at.")
    @JsonProperty("idleThresholdPercent")
    public int getIdleThresholdPercent() {
        return idleThresholdPercent;
    }

    /// @return True if idle chats are compacted, i.e. [#getIdleAfter()] is more than zero.
    @JsonIgnore
    public boolean isIdleCompactionEnabled() {
        return idleAfter != null && idleAfter.toMillis() > 0;
    }

    /// @return The threshold idle chats are compacted at: [#getIdleThresholdPercent()], but never above
    /// [#getThresholdPercent()], so an idle compaction always leaves a chat under the threshold a question compacts
    /// at.
    @JsonIgnore
    public int getEffectiveIdleThresholdPercent() {
        return Math.min(idleThresholdPercent, thresholdPercent);
    }

    /// @return True if [#getIdleThresholdPercent()] is below [#getThresholdPercent()], as it is meant to be.
    @AssertTrue(message = "idleThresholdPercent should be below thresholdPercent, or idle chats are only compacted "
                          + "once they are as full as a question would compact them at (thresholdPercent is used "
                          + "in its place)",
            payload = ValidationSeverity.Warning.class)
    @JsonIgnore
    public boolean isIdleThresholdBelowThreshold() {
        return idleThresholdPercent < thresholdPercent;
    }

    @Override
    public String toString() {
        return "AiCompactionConfig{" +
               "thresholdPercent=" + thresholdPercent +
               ", turnsKeptInFull=" + turnsKeptInFull +
               ", digestMaxResultRows=" + digestMaxResultRows +
               ", replyReserveTokens=" + replyReserveTokens +
               ", idleAfter=" + idleAfter +
               ", idleThresholdPercent=" + idleThresholdPercent +
               '}';
    }
}
