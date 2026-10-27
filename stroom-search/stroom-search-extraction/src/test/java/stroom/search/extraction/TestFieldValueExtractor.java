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

package stroom.search.extraction;

import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldValueExtractor {

    private static final String FIELD_NAME = "field";

    @Test
    void testKeyword() {
        assertThat(convert(FieldType.KEYWORD, "jbloggs"))
                .isEqualTo(ValString.create("jbloggs"));
    }

    @Test
    void testIpv4Address() {
        assertThat(convert(FieldType.IPV4_ADDRESS, "192.168.1.2"))
                .isEqualTo(ValString.create("192.168.1.2"));
    }

    @Test
    void testIpv6Address() {
        // Elasticsearch 'ip' fields map to IPV4_ADDRESS but can also hold IPv6 addresses
        assertThat(convert(FieldType.IPV4_ADDRESS, "2001:db8::1"))
                .isEqualTo(ValString.create("2001:db8::1"));
    }

    @Test
    void testText() {
        assertThat(convert(FieldType.TEXT, "the cat sat"))
                .isEqualTo(ValString.create("the cat sat"));
    }

    @Test
    void testDate_iso() {
        assertThat(convert(FieldType.DATE, "2026-10-23T11:19:40.000Z"))
                .isEqualTo(ValDate.create(1792754380000L));
    }

    @Test
    void testDate_isoWithoutMillis() {
        assertThat(convert(FieldType.DATE, "2026-10-23T11:19:40Z"))
                .isEqualTo(ValDate.create(1792754380000L));
    }

    @Test
    void testDate_epochMillis() {
        assertThat(convert(FieldType.DATE, "1792754380000"))
                .isEqualTo(ValDate.create(1792754380000L));
    }

    @Test
    void testDate_invalid() {
        assertThat(convert(FieldType.DATE, "not a date"))
                .isNull();
    }

    @Test
    void testLong() {
        assertThat(convert(FieldType.LONG, "-5"))
                .isEqualTo(ValLong.create(-5L));
    }

    @Test
    void testLong_beyondRangeOfLong() {
        assertThat(convert(FieldType.LONG, "18446744073709551615"))
                .isEqualTo(ValString.create("18446744073709551615"));
    }

    @Test
    void testLong_invalid() {
        assertThat(convert(FieldType.LONG, "abc"))
                .isNull();
    }

    @Test
    void testConvertList_keyword() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(FIELD_NAME);
        final FieldValueExtractor extractor = new FieldValueExtractor(
                fieldIndex,
                new IndexField[]{createField(FieldType.KEYWORD)});

        final List<FieldValue> fieldValues = extractor.convert(List.of(
                new StringFieldValue(FIELD_NAME, "jbloggs"),
                new StringFieldValue("unknown", "ignored")));

        assertThat(fieldValues)
                .extracting(FieldValue::value)
                .containsExactly(ValString.create("jbloggs"));
    }

    private Val convert(final FieldType fieldType, final String value) {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(FIELD_NAME);
        final FieldValueExtractor extractor = new FieldValueExtractor(
                fieldIndex,
                new IndexField[]{createField(fieldType)});
        return extractor.convert(0, value).value();
    }

    private IndexField createField(final FieldType fieldType) {
        return IndexFieldImpl.builder()
                .fldName(FIELD_NAME)
                .fldType(fieldType)
                .build();
    }
}
