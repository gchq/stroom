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

package stroom.gwt.workbench.framework.client.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestUrlParams {

    @ParameterizedTest
    @CsvSource(value = {
            "abc,abc",
            "VIOLATIONS.color-contrast,VIOLATIONS.color-contrast",
            "workbench/a11y/panel,workbench/a11y/panel",
            "a b,a%20b",
            "a&b=c,a%26b%3Dc",
            "a#b,a%23b",
            "a+b,a%2Bb",
            "é,%C3%A9",
            "€,%E2%82%AC",
            "😀,%F0%9F%98%80",
    })
    void testEncode(final String value, final String expected) {
        assertThat(UrlParams.encode(value)).isEqualTo(expected);
    }

    @Test
    void testEncode_nullAndEmpty() {
        assertThat(UrlParams.encode(null)).isEmpty();
        assertThat(UrlParams.encode("")).isEmpty();
    }

    @Test
    void testWithParameter_added() {
        assertThat(UrlParams.withParameter("http://host/?path=/story/a--b", "a11ySelection", "X.y"))
                .isEqualTo("http://host/?path=/story/a--b&a11ySelection=X.y");
        assertThat(UrlParams.withParameter("http://host/", "a", "1"))
                .isEqualTo("http://host/?a=1");
        assertThat(UrlParams.withParameter("http://host/?", "a", "1"))
                .isEqualTo("http://host/?a=1");
    }

    @Test
    void testWithParameter_replacesExisting() {
        // Regression test: copying a link twice used to add the parameters again
        final String url = "http://host/?path=/story/a--b&a11ySelection=OLD&addonPanel=x&a11ySelection=OLD2";
        assertThat(UrlParams.withParameter(url, "a11ySelection", "NEW"))
                .isEqualTo("http://host/?path=/story/a--b&addonPanel=x&a11ySelection=NEW");
    }

    @Test
    void testWithParameter_keepsFragment() {
        assertThat(UrlParams.withParameter("http://host/?path=/story/a--b#anchor", "a", "1"))
                .isEqualTo("http://host/?path=/story/a--b&a=1#anchor");
        assertThat(UrlParams.withParameter("http://host/#a?b=c", "a", "1"))
                .isEqualTo("http://host/?a=1#a?b=c");
    }

    @Test
    void testWithParameter_encodesValue() {
        assertThat(UrlParams.withParameter("http://host/?path=x", "a11ySelection", "V.rule&evil=1#x"))
                .isEqualTo("http://host/?path=x&a11ySelection=V.rule%26evil%3D1%23x");
    }

    @Test
    void testWithParameter_similarNamesKept() {
        assertThat(UrlParams.withParameter("http://host/?ab=1&a=2&a", "a", "3"))
                .isEqualTo("http://host/?ab=1&a=3");
    }
}
