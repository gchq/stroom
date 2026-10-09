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

package stroom.document.asset.impl;

import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The upload cap's default and its absent-value handling.
 *
 * <p>Both matter because of how this reaches production: the property is new, so every existing
 * deployment's config file omits it, and the {@code @JsonCreator} therefore receives {@code null}.
 * If that produced a {@code null} cap the check would be skipped and the limit would silently not
 * exist on precisely the installations it was added for.</p>
 */
class TestDocumentAssetConfig {

    @Test
    void testTheDefaultIsFiftyMebibytes() {
        assertThat(new DocumentAssetConfig().getMaxUploadSize())
                .isEqualTo(ByteSize.ofMebibytes(50));
    }

    /** An existing config file has no such property, so the creator sees null. */
    @Test
    void testAnAbsentValueFallsBackToTheDefaultRatherThanNoLimit() {
        final DocumentAssetConfig config = new DocumentAssetConfig(
                Map.of("png", "image/png"),
                "application/octet-stream",
                "asset_cache",
                false,
                Map.of("xml", "XML"),
                "TEXT",
                null);

        assertThat(config.getMaxUploadSize())
                .as("null must not disable the cap")
                .isEqualTo(ByteSize.ofMebibytes(50));
    }

    @Test
    void testAnExplicitValueIsHonoured() {
        final DocumentAssetConfig config = new DocumentAssetConfig(
                Map.of("png", "image/png"),
                "application/octet-stream",
                "asset_cache",
                false,
                Map.of("xml", "XML"),
                "TEXT",
                ByteSize.ofMebibytes(5));

        assertThat(config.getMaxUploadSize()).isEqualTo(ByteSize.ofMebibytes(5));
    }
}
