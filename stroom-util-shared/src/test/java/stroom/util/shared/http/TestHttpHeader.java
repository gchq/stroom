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

package stroom.util.shared.http;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestHttpHeader {

    @Test
    void duplicateNamesAreFoundIgnoringCaseAndSpaces() {
        assertThat(HttpHeader.findDuplicateName(List.of(
                new HttpHeader("X-Tenant", "a"),
                new HttpHeader("anthropic-version", "2023-06-01"),
                new HttpHeader(" x-tenant ", "b"))))
                .isEqualTo("x-tenant");
    }

    @Test
    void sameValueStillCountsAsADuplicate() {
        assertThat(HttpHeader.findDuplicateName(List.of(
                new HttpHeader("X-A", "1"),
                new HttpHeader("X-A", "1"))))
                .isEqualTo("X-A");
    }

    @Test
    void noDuplicates() {
        assertThat(HttpHeader.findDuplicateName(null)).isNull();
        assertThat(HttpHeader.findDuplicateName(List.of())).isNull();
        assertThat(HttpHeader.findDuplicateName(List.of(
                new HttpHeader("X-A", "1"),
                new HttpHeader("X-B", "1"))))
                .isNull();
    }

    @Test
    void blankNamesAndNullsAreIgnored() {
        assertThat(HttpHeader.findDuplicateName(Arrays.asList(
                new HttpHeader("", "1"),
                new HttpHeader("  ", "2"),
                new HttpHeader(null, "3"),
                null,
                null)))
                .isNull();
    }
}
