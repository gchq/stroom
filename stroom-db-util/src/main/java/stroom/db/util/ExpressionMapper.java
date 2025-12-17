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

package stroom.db.util;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ExpressionMapper implements Function<ExpressionItem, Condition> {

    private final CommonExpressionMapper expressionMapper;
    private final TermHandlerFactory termHandlerFactory;

    ExpressionMapper(final TermHandlerFactory termHandlerFactory,
                     final Function<ExpressionItem, Condition> delegateItemHandler) {
        expressionMapper = new CommonExpressionMapper(delegateItemHandler);
        this.termHandlerFactory = termHandlerFactory;
    }

    public void addHandler(final QueryField dataSourceField,
                           final Function<ExpressionTerm, Condition> handler) {
        expressionMapper.addHandler(dataSourceField, handler);
    }

    /**
     * Uses UUID for any {@link stroom.query.api.datasource.DocRefField}s
     */
    public <T> ExpressionMapper map(final QueryField dataSourceField,
                                    final Field<T> field,
                                    final Converter<T> converter) {
        addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                MultiConverter.wrapConverter(converter)));
        return this;
    }

    /**
     * Uses UUID or name for any {@link stroom.query.api.datasource.DocRefField}s depending on useName
     */
    public <T> ExpressionMapper map(final QueryField dataSourceField,
                                    final Field<T> field,
                                    final Converter<T> converter,
                                    final boolean useName) {
        addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                MultiConverter.wrapConverter(converter),
                useName));
        return this;
    }

    /**
     * Uses UUID for any {@link stroom.query.api.datasource.DocRefField}s
     */
    public <T> ExpressionMapper multiMap(final QueryField dataSourceField,
                                         final Field<T> field,
                                         final MultiConverter<T> converter) {
        addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                converter));
        return this;
    }

    /**
     * Uses UUID or name for any {@link stroom.query.api.datasource.DocRefField}s depending on useName
     */
    public <T> ExpressionMapper multiMap(final QueryField dataSourceField,
                                         final Field<T> field,
                                         final MultiConverter<T> converter,
                                         final boolean useName) {
        addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                converter,
                useName));
        return this;
    }

    public void ignoreField(final QueryField dataSourceField) {
        expressionMapper.ignoreField(dataSourceField);
    }

    @Override
    public Condition apply(final ExpressionItem expressionItem) {
        return expressionMapper.apply(expressionItem);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public interface Converter<T> {

        T apply(String value);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public interface MultiConverter<T> {

        /**
         * Converts each input value into 0-many output values, combining all the output lists
         * into a single list.
         */
        List<T> apply(final List<String> values);

        /**
         * Wraps a one to many function inside a {@link MultiConverter}. converter will be called once for each
         * value passed to {@link MultiConverter} so don't use this if converter is backed by SQL calls, instead
         * implement {@link MultiConverter} yourself.
         */
        static <T> MultiConverter<T> wrapSingleInputConverter(final Function<String, List<T>> converter) {
            return values -> {
                if (NullSafe.isEmptyCollection(values)) {
                    return Collections.emptyList();
                } else {
                    // Call the converter for each
                    return values.stream()
                            .filter(val -> !NullSafe.isBlankString(val))
                            .flatMap(val -> converter.apply(val).stream())
                            .toList();
                }
            };
        }

        /**
         * Wraps a one to one {@link Converter} function inside a {@link MultiConverter}.
         */
        static <T> MultiConverter<T> wrapConverter(final Converter<T> converter) {
            return values -> {
                if (NullSafe.isEmptyCollection(values)) {
                    return Collections.emptyList();
                } else {
                    return values.stream()
                            .filter(val -> !NullSafe.isBlankString(val))
                            .map(converter::apply)
                            .filter(Objects::nonNull) // Null items would NPE on Collectors.toList
                            .toList();
                }
            };
        }
    }
}
