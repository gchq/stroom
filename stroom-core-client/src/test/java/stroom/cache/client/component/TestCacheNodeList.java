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

package stroom.cache.client.component;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestCacheNodeList {

    @BeforeEach
    void setUp() {
        // The component's template binder is made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @ParameterizedTest
    @CsvSource({
            "HitCount, 1234567, '1,234,567'",
            "Size, 12, 12",
            "Size, n/a, n/a",
            "TotalLoadTime, 1234567, 1234567",
            "ExpireAfterWrite, PT10M, PT10M"})
    void testFormatValue(final String key, final String value, final String expected) {
        assertThat(CacheNodeList.formatValue(key, value)).isEqualTo(expected);
    }

    @Test
    void testColumnWidth() {
        // Short headings are a small column at least
        assertThat(CacheNodeList.columnWidth("Size")).isEqualTo(90);
        // Wide letters count for more than thin ones
        assertThat(CacheNodeList.columnWidth("Maximum Weight Wanted"))
                .isGreaterThan(CacheNodeList.columnWidth("little little little"));
    }
}
