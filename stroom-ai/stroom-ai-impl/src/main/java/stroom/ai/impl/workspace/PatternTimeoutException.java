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

package stroom.ai.impl.workspace;

/// Thrown when matching a regular expression the model wrote runs past [SafePattern#REGEX_TIME_LIMIT]: it
/// backtracks too much (ReDoS), and is stopped rather than left to run for ever. Calling again with the same
/// pattern changes nothing, so the message says to make the pattern simpler, not to call again.
public class PatternTimeoutException extends WorkspaceException {

    /// @param pattern The pattern (shown cut short if it is long), or null if it isn't known.
    public PatternTimeoutException(final String pattern) {
        super("The pattern " + shown(pattern) + " took too long to match (more than "
              + SafePattern.REGEX_TIME_LIMIT.toSeconds() + " s) and was stopped: it backtracks too much. Make it "
              + "simpler: anchor it, and avoid nested or overlapping repeats such as (a+)+, (.*)* or (\\w|\\d)+.");
    }

    private static String shown(final String pattern) {
        if (pattern == null) {
            return "given";
        }
        return "'" + (pattern.length() > 100
                ? pattern.substring(0, 100) + "..."
                : pattern) + "'";
    }
}
