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

import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.shared.ElasticNativeTypes;
import stroom.search.elastic.shared.UnsupportedTypeException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestElasticIndexFieldType {

    @Test
    public void testFromNativeType() throws UnsupportedTypeException {
        assertSame(FieldType.BOOLEAN,
                ElasticNativeTypes.fromNativeType("name", "boolean"));
        assertSame(FieldType.INTEGER,
                ElasticNativeTypes.fromNativeType("name", "integer"));
        assertSame(FieldType.LONG,
                ElasticNativeTypes.fromNativeType("name", "long"));
        assertSame(FieldType.FLOAT,
                ElasticNativeTypes.fromNativeType("name", "float"));
        assertSame(FieldType.DOUBLE,
                ElasticNativeTypes.fromNativeType("name", "double"));
        assertSame(FieldType.DATE,
                ElasticNativeTypes.fromNativeType("name", "date"));
        assertSame(FieldType.TEXT,
                ElasticNativeTypes.fromNativeType("name", "text"));
    }

    @Test
    public void testIsNumeric() throws UnsupportedTypeException {
        assertTrue(FieldType.INTEGER.isNumeric());
        assertTrue(FieldType.LONG.isNumeric());
        assertTrue(FieldType.FLOAT.isNumeric());
        assertTrue(FieldType.DOUBLE.isNumeric());
    }

    @Test
    void testNotFound() {
        Assertions.assertThatThrownBy(() -> {
                    ElasticNativeTypes.fromNativeType("name", "foo");
                })
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("foo");
    }
}
