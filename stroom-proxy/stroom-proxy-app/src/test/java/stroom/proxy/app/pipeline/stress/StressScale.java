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

package stroom.proxy.app.pipeline.stress;

/**
 * How hard to push, without editing the scenarios.
 * <p>
 * The default is sized so a full stress run finishes in well under a minute,
 * which is the only size anyone will actually run regularly. A soak run is the
 * same scenarios with the counts multiplied:
 * </p>
 * <pre>
 * ./gradlew :stroom-proxy:stroom-proxy-app:stressTest -PstressScale=20
 * </pre>
 * <p>
 * Scaling the load rather than adding separate "long" scenarios keeps one set of
 * invariants to maintain. A soak run that asserted something different from the
 * quick run would be a second suite pretending to be the same one.
 * </p>
 */
public final class StressScale {

    private static final String PROPERTY = "stress.scale";
    private static final int DEFAULT_SCALE = 1;

    private StressScale() {
    }

    public static int get() {
        final String raw = System.getProperty(PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SCALE;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(
                    "System property " + PROPERTY + " must be a positive integer but was '" + raw + "'", e);
        }
    }

    /**
     * @param base The count at scale 1.
     * @return The count to use for this run.
     */
    public static int payloads(final int base) {
        return base * get();
    }

    public static String describe() {
        return PROPERTY + "=" + get();
    }
}
