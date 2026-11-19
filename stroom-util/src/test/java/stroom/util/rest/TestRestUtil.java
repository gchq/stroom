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

package stroom.util.rest;

import stroom.util.shared.PathSafeNames;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRestUtil {

    @Test
    void checkPathSafeName_slash() {
        // A 400 whose body says why, which the UI shows as the reason
        assertThatThrownBy(() -> RestUtil.checkPathSafeName("A/B volumes"))
                .isInstanceOfSatisfying(BadRequestException.class, e -> {
                    assertThat(e.getResponse().getStatus())
                            .isEqualTo(400);
                    assertThat(e.getResponse().getEntity())
                            .isEqualTo(PathSafeNames.CONTAINS_SLASH_MESSAGE);
                });
    }

    @Test
    void checkPathSafeName_allowed() {
        assertThatNoException()
                .isThrownBy(() -> RestUtil.checkPathSafeName("Default Volume Group"));
    }
}
