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
import java.util.Objects;

/**
 * An item of type T that has a finite life as defined by its expiry time.
 *
 * @param <T>
 */
public class PerishableItem<T> implements HasExpiry {

    private final Instant expiryTime;
    private final T item;

    public PerishableItem(final Instant expiryTime, final T item) {
        this.expiryTime = Objects.requireNonNull(expiryTime);
        this.item = Objects.requireNonNull(item);
    }

    public PerishableItem(final Duration remaining, final T item) {
        this.expiryTime = Instant.now().plus(Objects.requireNonNull(remaining));
        this.item = Objects.requireNonNull(item);
    }

    @Override
    public Instant getExpireTime() {
        return expiryTime;
    }

    public T getItem() {
        return item;
    }

    @Override
    public String toString() {
        return "Perishable{" +
               "expiryTime=" + expiryTime +
               ", item=" + item +
               '}';
    }
}
