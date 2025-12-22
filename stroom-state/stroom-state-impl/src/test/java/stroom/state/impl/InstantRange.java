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

package stroom.state.impl;

import java.time.Instant;

public record InstantRange(Instant min, Instant max) {

    public static InstantRange combine(final InstantRange one, final InstantRange two) {
        final Instant min = Instant.ofEpochMilli(Math.min(one.min.toEpochMilli(), two.min.toEpochMilli()));
        final Instant max = Instant.ofEpochMilli(Math.max(one.max.toEpochMilli(), two.max.toEpochMilli()));
        return new InstantRange(min, max);
    }
}
