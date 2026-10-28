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

package stroom.main.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPageTitle {

    @Test
    void testWithTabLabel() {
        assertThat(PageTitle.build("Stroom", "MY_FEED"))
                .isEqualTo("Stroom - MY_FEED");
    }

    @Test
    void testWithCustomBaseTitle() {
        assertThat(PageTitle.build("Stroom PROD", "User Groups"))
                .isEqualTo("Stroom PROD - User Groups");
    }

    @Test
    void testTabLabelIsTrimmed() {
        assertThat(PageTitle.build("Stroom", "  MY_FEED  "))
                .isEqualTo("Stroom - MY_FEED");
    }

    @Test
    void testNullTabLabel() {
        assertThat(PageTitle.build("Stroom", null))
                .isEqualTo("Stroom");
    }

    @Test
    void testBlankTabLabel() {
        assertThat(PageTitle.build("Stroom", "   "))
                .isEqualTo("Stroom");
    }
}
