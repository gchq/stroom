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

package stroom.security.openid.api;

/**
 * How much live access one subject holds by way of internally minted tokens.
 * <p>
 * Counts usable tokens only - expired, revoked and spent ones are excluded, because the question this answers
 * is "what could this subject do right now", not "what have they ever been issued".
 * </p>
 *
 * @param tokenCount        How many usable tokens the subject holds, of any type.
 * @param nextExpiryMs      When the soonest of them expires, or null if there are none. Gives an administrator a
 *                          sense of how long access would persist if it were left alone.
 * @param latestExpiryMs    When the last of them expires, or null if there are none. This is how long access
 *                          would persist without intervention - typically driven by a refresh token, which
 *                          outlives access tokens by weeks.
 */
public record TokenSummary(int tokenCount,
                           Long nextExpiryMs,
                           Long latestExpiryMs) {

    public static final TokenSummary NONE = new TokenSummary(0, null, null);

    public boolean isEmpty() {
        return tokenCount == 0;
    }
}
