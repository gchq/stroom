/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.authentication;

import java.time.Duration;
import java.time.Instant;

/**
 * Something that has an expiry date/time
 */
public interface HasExpiry {

    /**
     * @return The instant when this object will expire or {@link Instant#MAX} if there
     * is no expiry.
     */
    Instant getExpireTime();

    default long getExpireTimeEpochMs() {
        return getExpireTime().toEpochMilli();
    }

    /**
     * @return True if this object has already expired.
     */
    default boolean hasExpired() {
        return Instant.now()
                .isAfter(getExpireTime());
    }

    /**
     * @return The duration until expiry. Will be negative if already expired.
     */
    default Duration getTimeTilExpired() {
        return Duration.between(Instant.now(), getExpireTime());
    }
}
