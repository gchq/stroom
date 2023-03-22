/*
 * Copyright 2017 Crown Copyright
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

package stroom.index.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.index.shared.IndexField;
import stroom.pipeline.filter.FieldValue;

import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FieldFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldFactory.class);

    public static LongField create(final IndexField indexField, final long initialValue) {
        return new LongField(indexField.getFieldName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static DoubleField createDouble(final IndexField indexField, final double initialValue) {
        return new DoubleField(indexField.getFieldName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static IntField createInt(final IndexField indexField, final int initialValue) {
        return new IntField(indexField.getFieldName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static FloatField createFloat(final IndexField indexField, final float initialValue) {
        return new FloatField(indexField.getFieldName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static Field create(final IndexField indexField, final String initialValue) {
        return new Field(indexField.getFieldName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static Field create(final FieldValue fieldValue) {
        final IndexField indexField = fieldValue.field();
        final Val value = fieldValue.value();

        org.apache.lucene.document.Field field = null;
        switch (indexField.getFieldType()) {
            case LONG_FIELD, NUMERIC_FIELD, ID -> {
                try {
                    field = FieldFactory.create(indexField, value.toLong());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case BOOLEAN_FIELD -> {
                // TODo : We are indexing boolean as String, not sure this is right.
                field = FieldFactory.create(indexField, value.toString());
            }
            case INTEGER_FIELD -> {
                try {
                    field = FieldFactory.createInt(indexField, value.toInteger());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case FLOAT_FIELD -> {
                try {
                    field = FieldFactory.createFloat(indexField, value.toDouble().floatValue());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case DOUBLE_FIELD -> {
                try {
                    field = FieldFactory.createDouble(indexField, value.toDouble());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case DATE_FIELD -> {
                try {
                    field = FieldFactory.create(indexField, value.toLong());
                } catch (final RuntimeException e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case FIELD -> {
                field = FieldFactory.create(indexField, value.toString());
            }
        }

        return field;
    }
}
