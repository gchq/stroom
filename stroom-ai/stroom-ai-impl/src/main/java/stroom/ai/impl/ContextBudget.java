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

/// How many tokens of a model's context window a chat may use, and how full it may get before it is compacted.
///
/// @param budgetTokens     The context window less a reserve for the reply, or 0 if the window is not known.
/// @param thresholdPercent How full, as a percentage of the budget, the context may get before compacting.
public record ContextBudget(long budgetTokens, int thresholdPercent) {

    /// A budget for a model whose context window is not known: nothing is compacted until the model says the
    /// context is too large.
    public static final ContextBudget UNKNOWN = new ContextBudget(0, 100);

    /// @return True if the model's context window is known.
    public boolean isKnown() {
        return budgetTokens > 0;
    }

    /// @param tokens An estimate of the tokens a request would use.
    /// @return True if the window is known and the request would take it past the threshold.
    public boolean isOver(final long tokens) {
        return isKnown() && tokens * 100 >= budgetTokens * thresholdPercent;
    }
}
