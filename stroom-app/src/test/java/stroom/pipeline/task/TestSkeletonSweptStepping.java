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

package stroom.pipeline.task;

import stroom.pipeline.stepping.store.SteppingConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * The whole golden corpus again, with {@code stepping.skeletonSweep} ON: every scripted step sequence in
 * {@link TestFullTranslationTaskAndStepping}, over a stream whose first capture was truncated at the record
 * boundary and whose below-boundary IO is materialised on demand.
 * <p>
 * This is the acceptance gate for the skeleton sweep: R1 (fidelity) says the mode must be invisible in what
 * is served, and the corpus is the only thing that can say so across every feed type and step type at once.
 * The reader/text feeds earn their keep here too - they have no parser, so no boundary, and must fall back
 * to the ordinary full sweep untouched.
 * <p>
 * Runs as its own class (own thread, own database, own injector) so the config override cannot leak into the
 * plain-mode run of the same corpus; belt and braces, the mapper is still cleared after each test.
 */
class TestSkeletonSweptStepping extends TestFullTranslationTaskAndStepping {

    @BeforeEach
    void enableSkeletonSweep() {
        setConfigValueMapper(SteppingConfig.class, config -> config.withSkeletonSweep(true));
    }

    @AfterEach
    void restoreConfig() {
        clearConfigValueMapper();
    }
}
