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

package stroom.entity.client.presenter;

import stroom.entity.client.presenter.HtmlSanitisePolicy.ElementAction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestHtmlSanitisePolicy {

    @ParameterizedTest
    @CsvSource({
            "p, KEEP",
            "TABLE, KEEP",
            "a, KEEP",
            "code, KEEP",
            "img, IMAGE",
            "IMG, IMAGE",
            "script, DROP",
            "SCRIPT, DROP",
            "style, DROP",
            "iframe, DROP",
            "svg, DROP",
            "form, DROP",
            "object, DROP",
            "embed, DROP",
            "math, DROP",
            "template, DROP",
            "font, UNWRAP",
            "center, UNWRAP",
            "custom-element, UNWRAP"
    })
    void testElementAction(final String tag, final ElementAction expected) {
        assertThat(HtmlSanitisePolicy.getElementAction(tag)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1",
            "  https://example.com  ",
            "HTTPS://EXAMPLE.COM",
            "mailto:someone@example.com",
            "#heading",
            "?action=open-doc&docType=Feed&docUuid=4a2f6a1e-0f6e-4c55-9d0c-123456789abc",
            "?action=open-stream&id=12"
    })
    void testSafeLinkUrls(final String url) {
        assertThat(HtmlSanitisePolicy.isSafeLinkUrl(url)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            " javascript:alert(1)",
            "java\tscript:alert(1)",
            "java\nscript:alert(1)",
            "vbscript:msgbox(1)",
            "data:text/html;base64,PHNjcmlwdD4=",
            "//example.com",
            "/api/some/endpoint",
            "relative/path",
            "file:///etc/passwd",
            "   "
    })
    void testUnsafeLinkUrls(final String url) {
        assertThat(HtmlSanitisePolicy.isSafeLinkUrl(url)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "a, href, https://example.com, https://example.com",
            "a, HREF, https://example.com, https://example.com",
            "a, title, A title, A title",
            "code, class, language-java, language-java",
            "span, class, token keyword, token keyword",
            "td, align, LEFT, left",
            "th, style, text-align:center;, text-align:center;",
            "td, style, 'text-align: right', text-align:right;",
            "input, type, checkbox, checkbox",
            "input, checked, '', checked",
            "ol, start, 3, 3",
            "details, open, '', open"
    })
    void testAllowedAttributes(final String tag, final String attr, final String value, final String expected) {
        assertThat(HtmlSanitisePolicy.filterAttribute(tag, attr, value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "a, href, javascript:alert(1)",
            "a, onclick, alert(1)",
            "p, onmouseover, alert(1)",
            "img, onerror, alert(1)",
            "div, style, position:fixed;top:0",
            "td, style, text-align:left;background:url(https://example.com)",
            "p, class, some-app-class",
            "span, class, 'evil\"><script>'",
            "div, id, heading",
            "p, title, A title",
            "input, type, text",
            "ol, start, 1e9",
            "a, target, _self",
            "a, ping, https://example.com",
            "a, srcdoc, '<script>'"
    })
    void testRemovedAttributes(final String tag, final String attr, final String value) {
        assertThat(HtmlSanitisePolicy.filterAttribute(tag, attr, value)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "input, checkbox, true",
            "INPUT, CHECKBOX, true",
            "input, text, false",
            "input, password, false",
            "input, , false"
    })
    void testAllowedInput(final String tag, final String type, final boolean expected) {
        assertThat(HtmlSanitisePolicy.isAllowedInput(tag, type)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "'data:image/png;base64,iVBORw0KGgo=', true",
            "'data:image/jpeg;base64,/9j/4AAQ', true",
            "'DATA:IMAGE/GIF;base64,R0lGOD', true",
            "'data:image/svg+xml;base64,PHN2Zz4=', false",
            "'data:text/html;base64,PHNjcmlwdD4=', false",
            "'https://example.com/a.png', false",
            "'//example.com/a.png', false",
            "'', false"
    })
    void testInlineImage(final String src, final boolean expected) {
        assertThat(HtmlSanitisePolicy.isInlineImage(src)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "'https://example.com/?data=secret', 'https://example.com/?data=secret'",
            "'javascript:alert(1)', ",
            "'/relative.png', "
    })
    void testImageReplacementUrl(final String src, final String expected) {
        assertThat(HtmlSanitisePolicy.imageReplacementUrl(src)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "'A chart', '[Image not loaded: A chart]'",
            "'', '[Image not loaded]'",
            ", '[Image not loaded]'"
    })
    void testImageReplacementText(final String alt, final String expected) {
        assertThat(HtmlSanitisePolicy.imageReplacementText(alt)).isEqualTo(expected);
    }
}
