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

package stroom.ai.api;

/**
 * The outcome of a summary.
 * <p>
 * A caller with someone waiting on an answer wants something to show either way, and one that is
 * decorating a document only wants to decorate it when there is something worth showing. Keeping the two
 * apart stops "No data available for analysis." being presented as if it were a summary.
 * </p>
 *
 * @param text       Always something readable, whether or not a summary was produced.
 * @param summarised Whether {@link #text} summarises the data. False where there was nothing to summarise
 *                   or nothing came back, in which case the text says which.
 */
public record TableSummaryResult(String text, boolean summarised) {

    public static TableSummaryResult summarised(final String text) {
        return new TableSummaryResult(text, true);
    }

    /**
     * @param reason Why there is no summary, in terms a reader can act on.
     */
    public static TableSummaryResult notSummarised(final String reason) {
        return new TableSummaryResult(reason, false);
    }
}
