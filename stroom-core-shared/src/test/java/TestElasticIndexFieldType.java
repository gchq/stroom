/*
 * Copyright 2021 Crown Copyright
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

import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.shared.ElasticNativeTypes;
import stroom.search.elastic.shared.UnsupportedTypeException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;


public class TestElasticIndexFieldType {

    @Test
    public void testFromNativeType() throws UnsupportedTypeException {
        Assertions.assertThat(ElasticNativeTypes.fromNativeType("name", "boolean"))
                .isSameAs(FieldType.BOOLEAN);
        Assertions.assertThat(ElasticNativeTypes.fromNativeType("name", "integer"))
                .isSameAs(FieldType.INTEGER);
        Assertions.assertThat(ElasticNativeTypes.fromNativeType("name", "long"))
                .isSameAs(FieldType.LONG);
        Assertions.assertThat(ElasticNativeTypes.fromNativeType("name", "float"))
                .isSameAs(FieldType.FLOAT);
        Assertions.assertThat(ElasticNativeTypes.fromNativeType("name", "double"))
                .isSameAs(FieldType.DOUBLE);
        Assertions.assertThat(ElasticNativeTypes.fromNativeType("name", "date"))
                .isSameAs(FieldType.DATE);
        Assertions.assertThat(ElasticNativeTypes.fromNativeType("name", "text"))
                .isSameAs(FieldType.TEXT);
    }

    @Test
    public void testIsNumeric() throws UnsupportedTypeException {
        Assertions.assertThat(FieldType.INTEGER.isNumeric())
                .isTrue();
        Assertions.assertThat(FieldType.LONG.isNumeric())
                .isTrue();
        Assertions.assertThat(FieldType.FLOAT.isNumeric())
                .isTrue();
        Assertions.assertThat(FieldType.DOUBLE.isNumeric())
                .isTrue();
    }

    @Test
    void testNotFound() {
        Assertions.assertThatThrownBy(
                        () -> {
                            ElasticNativeTypes.fromNativeType("name", "foo");
                        })
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("foo");
    }
}
