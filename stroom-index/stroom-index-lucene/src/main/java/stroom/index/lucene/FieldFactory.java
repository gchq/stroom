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

package stroom.index.lucene;

import stroom.index.shared.LuceneIndexField;
import stroom.query.api.datasource.IndexField;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.FieldValue;

import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.IndexableField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FieldFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldFactory.class);

    private final DenseVectorFieldCreatorFactory denseVectorFieldCreatorFactory;
    private final Map<IndexField, DenseVectorFieldCreator> denseVectorFieldCreatorMap = new ConcurrentHashMap<>();

    public FieldFactory() {
        this.denseVectorFieldCreatorFactory = null;
    }

    public FieldFactory(final DenseVectorFieldCreatorFactory denseVectorFieldCreatorFactory) {
        this.denseVectorFieldCreatorFactory = denseVectorFieldCreatorFactory;
    }

    private Collection<Field> createLong(final LuceneIndexField indexField, final long value) {
        return Collections.singleton(new LongField(indexField.getFldName(),
                value,
                indexField.isStored()
                        ? Store.YES
                        : Store.NO));
    }

    private Collection<Field> createDouble(final LuceneIndexField indexField, final double value) {
        return Collections.singleton(new DoubleField(indexField.getFldName(),
                value,
                indexField.isStored()
                        ? Store.YES
                        : Store.NO));
    }

    private Collection<Field> createInt(final LuceneIndexField indexField, final int value) {
        return Collections.singleton(new IntField(indexField.getFldName(),
                value,
                indexField.isStored()
                        ? Store.YES
                        : Store.NO));
    }

    private Collection<Field> createFloat(final LuceneIndexField indexField, final float value) {
        return Collections.singleton(new FloatField(indexField.getFldName(),
                value,
                indexField.isStored()
                        ? Store.YES
                        : Store.NO));
    }

    private Collection<Field> create(final LuceneIndexField indexField, final String value) {
        return Collections.singleton(new Field(indexField.getFldName(), value, FieldTypeFactory.create(indexField)));
    }

    public Collection<Field> create(final FieldValue fieldValue) {
        final IndexField indexField = fieldValue.field();
        final LuceneIndexField luceneIndexField = LuceneIndexField
                .fromIndexField(indexField);

        final Val value = fieldValue.value();

        Collection<org.apache.lucene.document.Field> fields = Collections.emptyList();
        switch (indexField.getFldType()) {
            case LONG, ID, DATE -> {
                try {
                    fields = createLong(luceneIndexField, value.toLong());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case BOOLEAN -> // TODO : We are indexing boolean as String, not sure this is right.
                    fields = create(luceneIndexField, value.toString());
            case INTEGER -> {
                try {
                    fields = createInt(luceneIndexField, value.toInteger());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case FLOAT -> {
                try {
                    fields = createFloat(luceneIndexField, value.toDouble().floatValue());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case DOUBLE -> {
                try {
                    fields = createDouble(luceneIndexField, value.toDouble());
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            }
            case TEXT -> fields = create(luceneIndexField, value.toString());
            case DENSE_VECTOR -> {
                if (denseVectorFieldCreatorFactory != null) {
                    final DenseVectorFieldCreator denseVectorFieldCreator =
                            denseVectorFieldCreatorMap.computeIfAbsent(indexField,
                                    denseVectorFieldCreatorFactory::create);
                    fields = denseVectorFieldCreator.getFields(fieldValue.value().toString());
                }
            }
        }

        return fields;
    }

    public Val convertValue(final IndexField indexField, final IndexableField indexableField) {
        switch (indexField.getFldType()) {
            case LONG, ID -> {
                final long val = indexableField.numericValue().longValue();
                return ValLong.create(val);
            }
            case BOOLEAN -> {
                // TODO : We are indexing boolean as String, not sure this is right.
                final boolean val = Boolean.parseBoolean(indexableField.stringValue());
                return ValBoolean.create(val);
            }
            case INTEGER -> {
                final int val = indexableField.numericValue().intValue();
                return ValInteger.create(val);
            }
            case FLOAT -> {
                final float val = indexableField.numericValue().floatValue();
                return ValFloat.create(val);
            }
            case DOUBLE -> {
                final double val = indexableField.numericValue().doubleValue();
                return ValDouble.create(val);
            }
            case DATE -> {
                final long val = indexableField.numericValue().longValue();
                return ValDate.create(val);
            }
            case TEXT -> {
                return ValString.create(indexableField.stringValue());
            }
        }
        return ValString.create(indexableField.stringValue());
    }
}
