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

package stroom.query.client.result.component;

import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.datasource.FieldType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestConditionalFormattingRulesController {

    @Test
    void fieldTypeOfEachFormat() {
        assertThat(getFieldType(null)).isEqualTo(FieldType.TEXT);
        assertThat(getFieldType(Format.GENERAL)).isEqualTo(FieldType.TEXT);
        assertThat(getFieldType(Format.TEXT)).isEqualTo(FieldType.TEXT);
        assertThat(getFieldType(Format.NUMBER)).isEqualTo(FieldType.LONG);
        assertThat(getFieldType(Format.DURATION)).isEqualTo(FieldType.LONG);
        assertThat(getFieldType(Format.DATE_TIME)).isEqualTo(FieldType.DATE);
    }

    private static FieldType getFieldType(final Format format) {
        return ConditionalFormattingRulesController.buildDsField(
                Column.builder().name("Elapsed").format(format).build()).getFldType();
    }
}
