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

package stroom.ai.client;

/// When the chat can stop polling a run that is waiting for the user.
///
/// A run waits for the user once it has stored what it asks them (a form or a request for approval), and does
/// nothing until they answer, so there is no need to keep polling it. But polling stops only once what it asks
/// is shown: if a poll saw the run waiting before the message reached the chat, stopping would leave the user
/// with nothing to answer. That is limited to [#MAX_WAIT_FOR_PROMPT_MS], in case a run waits for something the
/// chat doesn't show.
final class AiPolling {

    /// How long to keep polling a run waiting for the user while nothing it asks is shown.
    static final long MAX_WAIT_FOR_PROMPT_MS = 60_000;

    private AiPolling() {
        // Static methods only.
    }

    /// @param runWaiting       True if the run is waiting for the user.
    /// @param promptShown      True if a form or request for approval waiting for an answer is shown.
    /// @param waitingSinceMs   When polling first saw the run waiting with nothing it asks shown, or 0.
    /// @param keepPollingUntil Until when to keep polling anyway, e.g. as the user has just answered.
    /// @param nowMs            The time now.
    /// @return True if polling can stop until the user does something.
    static boolean canStopForUser(final boolean runWaiting,
                                  final boolean promptShown,
                                  final long waitingSinceMs,
                                  final long keepPollingUntil,
                                  final long nowMs) {
        if (!runWaiting || nowMs <= keepPollingUntil) {
            return false;
        }
        return promptShown || (waitingSinceMs > 0 && nowMs - waitingSinceMs >= MAX_WAIT_FOR_PROMPT_MS);
    }
}
