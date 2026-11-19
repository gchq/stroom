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

package stroom.config.global.impl;

import stroom.quickfilter.shared.QuickFilterHistoryKey;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestQuickFilterHistoryResourceImpl {

    @Test
    void testValidKeys() {
        assertThat(QuickFilterHistoryResourceImpl.validate(new QuickFilterHistoryKey("dependencies", null)))
                .isNotNull();
        assertThat(QuickFilterHistoryResourceImpl.validate(new QuickFilterHistoryKey("globalProperties", "")))
                .isNotNull();
        assertThat(QuickFilterHistoryResourceImpl.validate(new QuickFilterHistoryKey(
                "traces", "0f5a2c3e-8b1d-4c2a-9e7f-1a2b3c4d5e6f")))
                .isNotNull();
        assertThat(QuickFilterHistoryResourceImpl.validate(new QuickFilterHistoryKey("a-b_c9", null)))
                .isNotNull();
    }

    @Test
    void testNullKeyIsBadRequest() {
        assertThatThrownBy(() -> QuickFilterHistoryResourceImpl.validate(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void testContextIsConstrained() {
        // Stored in a varchar(64) and used as an identifier, so it is bounded and character-limited.
        assertThatThrownBy(() -> QuickFilterHistoryResourceImpl.validate(new QuickFilterHistoryKey("", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> QuickFilterHistoryResourceImpl.validate(
                new QuickFilterHistoryKey("x".repeat(65), null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> QuickFilterHistoryResourceImpl.validate(
                new QuickFilterHistoryKey("has space", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> QuickFilterHistoryResourceImpl.validate(
                new QuickFilterHistoryKey("../etc", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void testDataSourceMustBeAUuidOrAbsent() {
        assertThatThrownBy(() -> QuickFilterHistoryResourceImpl.validate(
                new QuickFilterHistoryKey("traces", "not-a-uuid")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> QuickFilterHistoryResourceImpl.validate(
                new QuickFilterHistoryKey("traces", "0f5a2c3e-8b1d-4c2a-9e7f-1a2b3c4d5e6f-extra")))
                .isInstanceOf(BadRequestException.class);
    }
}
