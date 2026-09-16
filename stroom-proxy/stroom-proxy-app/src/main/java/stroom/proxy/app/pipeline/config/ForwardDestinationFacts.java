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

package stroom.proxy.app.pipeline.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * What the pipeline validator needs to know about an enabled forward destination: enough to check
 * that the pipeline block names its queue and store when there are several, that its give-up
 * destination is shared in shared mode, and that the queue's bounds fit its retry bounds
 * ({@code designs/stages/forward.md} §4.3, §4.4).
 *
 * @param longestRetryDelay The longest wait the destination serves between attempts, with the claim
 *                          held throughout: the flat delay, or the cap when the delay grows.
 * @param giveUpDirectory   Where the destination gives up to when that is a directory; null when it
 *                          is an S3 bucket, which is shared by nature.
 */
public record ForwardDestinationFacts(String name,
                                      Duration maxRetryAge,
                                      Duration retryDelay,
                                      Duration longestRetryDelay,
                                      Path giveUpDirectory) {

    public ForwardDestinationFacts {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(maxRetryAge, "maxRetryAge");
        Objects.requireNonNull(retryDelay, "retryDelay");
        Objects.requireNonNull(longestRetryDelay, "longestRetryDelay");
    }
}
