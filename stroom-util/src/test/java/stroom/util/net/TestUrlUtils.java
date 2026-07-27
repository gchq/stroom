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

package stroom.util.net;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class TestUrlUtils {

    @Test
    void defaultPortsAreResolvedSoTheyCompareEqual() {
        assertThat(UrlUtils.toOrigin("https://example.com"))
                .isEqualTo(UrlUtils.toOrigin("https://example.com:443"))
                .isEqualTo("https://example.com:443");
        assertThat(UrlUtils.toOrigin("http://example.com"))
                .isEqualTo(UrlUtils.toOrigin("http://example.com:80"))
                .isEqualTo("http://example.com:80");
    }

    @Test
    void schemeAndHostAreLowerCased() {
        assertThat(UrlUtils.toOrigin("HTTPS://Example.COM/Path"))
                .isEqualTo("https://example.com:443");
    }

    @Test
    void pathQueryAndFragmentAreIgnored() {
        assertThat(UrlUtils.toOrigin("https://example.com:8443/a/b?x=1#frag"))
                .isEqualTo("https://example.com:8443");
    }

    @Test
    void differentOriginsDoNotMatch() {
        assertThat(UrlUtils.toOrigin("https://example.com"))
                .isNotEqualTo(UrlUtils.toOrigin("https://evil.example.com"))
                .isNotEqualTo(UrlUtils.toOrigin("http://example.com"))          // scheme differs
                .isNotEqualTo(UrlUtils.toOrigin("https://example.com:8443"));   // port differs
    }

    @Test
    void nullBlankMalformedOrSchemelessValuesReturnNull() {
        assertThat(UrlUtils.toOrigin((String) null)).isNull();
        assertThat(UrlUtils.toOrigin("null")).isNull();            // the literal opaque-origin value
        assertThat(UrlUtils.toOrigin("/relative/path")).isNull();  // no scheme/host
        assertThat(UrlUtils.toOrigin("not a uri ^^")).isNull();    // unparseable
    }

    @Test
    void isSameOrigin_acceptsSameOriginAndRootRelative() {
        final URI base = URI.create("https://stroom.example.com/");

        assertThat(UrlUtils.isSameOrigin("https://stroom.example.com/some/page?x=1", base)).isTrue();
        assertThat(UrlUtils.isSameOrigin("https://stroom.example.com:443/", base)).isTrue(); // default port
        assertThat(UrlUtils.isSameOrigin("/some/page", base)).isTrue();                      // root-relative
    }

    @Test
    void isSameOrigin_rejectsOffOriginAndTrickyValues() {
        final URI base = URI.create("https://stroom.example.com/");

        assertThat(UrlUtils.isSameOrigin("https://evil.example.com/", base)).isFalse();  // other host
        assertThat(UrlUtils.isSameOrigin("http://stroom.example.com/", base)).isFalse(); // other scheme
        assertThat(UrlUtils.isSameOrigin("https://stroom.example.com:8443/", base)).isFalse(); // other port
        assertThat(UrlUtils.isSameOrigin("//evil.example.com/", base)).isFalse();        // protocol-relative
        assertThat(UrlUtils.isSameOrigin("javascript:alert(1)", base)).isFalse();        // opaque scheme
        assertThat(UrlUtils.isSameOrigin(null, base)).isFalse();
        assertThat(UrlUtils.isSameOrigin("  ", base)).isFalse();
    }

    @Test
    void isSameOrigin_rejectsBackslashAndControlCharAuthorityBypass() {
        final URI base = URI.create("https://stroom.example.com/");

        // A browser folds '\' to '/', so a leading "/\" resolves to a protocol-relative "//host".
        assertThat(UrlUtils.isSameOrigin("/\\evil.example.com", base)).isFalse();
        assertThat(UrlUtils.isSameOrigin("/\\/evil.example.com", base)).isFalse();
        assertThat(UrlUtils.isSameOrigin("/path\\evil.example.com", base)).isFalse();

        // A browser strips tab/newline/CR before parsing, which can likewise change the effective origin.
        assertThat(UrlUtils.isSameOrigin("/\t/evil.example.com", base)).isFalse();
        assertThat(UrlUtils.isSameOrigin("/\n/evil.example.com", base)).isFalse();
        assertThat(UrlUtils.isSameOrigin("/\r/evil.example.com", base)).isFalse();

        // A genuine root-relative path (including a percent-encoded backslash, which the browser keeps
        // encoded and so does not fold) is still accepted.
        assertThat(UrlUtils.isSameOrigin("/some/page", base)).isTrue();
        assertThat(UrlUtils.isSameOrigin("/some/page%5Cx", base)).isTrue();
    }
}
