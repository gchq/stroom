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

package stroom.util.shared;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestPathSafeNames {

    @ParameterizedTest
    @ValueSource(strings = {"A/B volumes", "/", "volumes/", "a/b/c"})
    void validate_slash(final String name) {
        assertThat(PathSafeNames.validate(name))
                .isEqualTo(PathSafeNames.CONTAINS_SLASH_MESSAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Default Volume Group", "A-B_C.D", "A\\B", "Group (2)", "Grüppe"})
    void validate_allowed(final String name) {
        // Blank names are checked separately by the dialogs
        assertThat(PathSafeNames.validate(name))
                .isNull();
    }
}
