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

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

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
                null,
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
                ByteSize.ofMebibytes(5),
                null);

        assertThat(config.getMaxUploadSize()).isEqualTo(ByteSize.ofMebibytes(5));
    }

    /// `maxUploadSize` is read from a fresh config on every upload, so a change takes effect live.
    ///
    /// Regression test: the getter was inserted between `getMimetypes()` and its
    /// `@RequiresRestart`, so the annotation moved onto the wrong property.
    @Test
    void testMaxUploadSizeDoesNotRequireRestart() throws NoSuchMethodException {
        assertThat(DocumentAssetConfig.class.getMethod("getMaxUploadSize")
                .isAnnotationPresent(RequiresRestart.class))
                .isFalse();
    }

    /// `mimetypes` is cached by the singleton `DocumentAssetServlet` at construction, so a change
    /// is ignored until restart and the property must say so.
    @Test
    void testMimetypesRequiresRestart() throws NoSuchMethodException {
        assertThat(DocumentAssetConfig.class.getMethod("getMimetypes")
                .isAnnotationPresent(RequiresRestart.class))
                .isTrue();
    }

    @Test
    void testAllowedImageOriginsAreEmptyByDefault() {
        assertThat(new DocumentAssetConfig().getAllowedImageOrigins()).isEmpty();
        assertThat(configWithAllowedImageOrigins((String[]) null).getAllowedImageOrigins()).isEmpty();
    }

    /// A blank item in a YAML list reads as null.
    @Test
    void testANullAllowedImageOriginIsDropped() {
        assertThat(configWithAllowedImageOrigins("https://*.tile.osm.org", null).getAllowedImageOrigins())
                .containsExactly("https://*.tile.osm.org");
    }

    /// The servlet reads `allowedImageOrigins` from a fresh config on every request.
    @Test
    void testAllowedImageOriginsDoNotRequireRestart() throws NoSuchMethodException {
        assertThat(DocumentAssetConfig.class.getMethod("getAllowedImageOrigins")
                .isAnnotationPresent(RequiresRestart.class))
                .isFalse();
    }

    @Test
    void testTheAllowedImageOriginPatternAcceptsHttpAndHttpsHosts() {
        final Pattern pattern = Pattern.compile(DocumentAssetConfig.ALLOWED_IMAGE_ORIGIN_PATTERN);

        assertThat(Arrays.asList(
                "https://*.tile.osm.org",
                "https://maps.example.com:8443",
                "https://tiles.example.com/osm/",
                "http://tiles.internal:8080",
                "http://localhost",
                "https://10.0.0.5:*"))
                .allMatch(origin -> pattern.matcher(origin).matches());
    }

    @Test
    void testTheAllowedImageOriginPatternRefusesAnythingThatCouldAlterThePolicy() {
        final Pattern pattern = Pattern.compile(DocumentAssetConfig.ALLOWED_IMAGE_ORIGIN_PATTERN);

        assertThat(Arrays.asList(
                "",
                " ",
                "https://a.example.com; script-src *",
                "https://a.example.com https://b.example.com",
                "https://a.example.com,https://b.example.com",
                "https://a.example.com\t",
                "'unsafe-inline'",
                "\"https://a.example.com\""))
                .noneMatch(origin -> pattern.matcher(origin).matches());
    }

    /// Each of these would let any visualisation script send data to any host as image requests.
    @Test
    void testTheAllowedImageOriginPatternRefusesSourcesThatAllowAnyHost() {
        final Pattern pattern = Pattern.compile(DocumentAssetConfig.ALLOWED_IMAGE_ORIGIN_PATTERN);

        assertThat(Arrays.asList(
                "*",
                "https:",
                "http:",
                "blob:",
                "data:",
                "https://*",
                "https://*.",
                "*.tile.osm.org",
                "tile.osm.org",
                "ftp://tiles.example.com",
                "javascript:alert(1)"))
                .noneMatch(origin -> pattern.matcher(origin).matches());
    }

    private DocumentAssetConfig configWithAllowedImageOrigins(final String... allowedImageOrigins) {
        return new DocumentAssetConfig(
                Map.of("png", "image/png"),
                "application/octet-stream",
                "asset_cache",
                false,
                Map.of("xml", "XML"),
                "TEXT",
                null,
                allowedImageOrigins == null
                        ? null
                        : Arrays.asList(allowedImageOrigins));
    }
}
