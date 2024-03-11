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

package stroom.index.lucene553;

import stroom.index.shared.LuceneIndexField;
import stroom.query.language.functions.Val;
import stroom.search.extraction.FieldValue;

import org.apache.lucene553.document.DoubleField;
import org.apache.lucene553.document.Field;
import org.apache.lucene553.document.FloatField;
import org.apache.lucene553.document.IntField;
import org.apache.lucene553.document.LongField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FieldFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldFactory.class);

    public static LongField create(final LuceneIndexField indexField, final long initialValue) {
        return new LongField(indexField.getName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static DoubleField createDouble(final LuceneIndexField indexField, final double initialValue) {
        return new DoubleField(indexField.getName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static IntField createInt(final LuceneIndexField indexField, final int initialValue) {
        return new IntField(indexField.getName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static FloatField createFloat(final LuceneIndexField indexField, final float initialValue) {
        return new FloatField(indexField.getName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static Field create(final LuceneIndexField indexField, final String initialValue) {
        return new Field(indexField.getName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static Field create(final FieldValue fieldValue) {
        final LuceneIndexField indexField = fieldValue.field();
        final Val value = fieldValue.value();

        org.apache.lucene553.document.Field field = null;
        switch (indexField.getType()) {
            case LONG, ID -> {
                try {
                    field = FieldFactory.create(indexField, value.toLong());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case BOOLEAN -> {
                // TODo : We are indexing boolean as String, not sure this is right.
                field = FieldFactory.create(indexField, value.toString());
            }
            case INTEGER -> {
                try {
                    field = FieldFactory.createInt(indexField, value.toInteger());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case FLOAT -> {
                try {
                    field = FieldFactory.createFloat(indexField, value.toDouble().floatValue());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case DOUBLE -> {
                try {
                    field = FieldFactory.createDouble(indexField, value.toDouble());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case DATE -> {
                try {
                    field = FieldFactory.create(indexField, value.toLong());
                } catch (final RuntimeException e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case TEXT -> {
                field = FieldFactory.create(indexField, value.toString());
            }
        }

        return field;
    }
}
