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

package stroom.planb.impl.db.trace;

import stroom.pathways.shared.otel.trace.NanoTime;

import java.time.Instant;

public class NanoTimeUtil {

    public static NanoTime now() {
        return fromInstant(Instant.now());
    }

    public static NanoTime fromInstant(final Instant instant) {
        return new NanoTime(instant.getEpochSecond(), instant.getNano());
    }

    public static Instant toInstant(final NanoTime nanoTime) {
        return Instant.ofEpochSecond(nanoTime.getSeconds(), nanoTime.getNanos());
    }
}
