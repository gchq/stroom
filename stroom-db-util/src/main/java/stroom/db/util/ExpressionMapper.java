package stroom.db.util;

import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionItem;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpressionMapper implements Function<ExpressionItem, Condition> {

    private final CommonExpressionMapper expressionMapper;
    private final TermHandlerFactory termHandlerFactory;

    ExpressionMapper(final TermHandlerFactory termHandlerFactory,
                     final Function<ExpressionItem, Condition> delegateItemHandler) {
        expressionMapper = new CommonExpressionMapper(delegateItemHandler);
        this.termHandlerFactory = termHandlerFactory;
    }

    /**
     * Uses UUID for any {@link stroom.datasource.api.v2.DocRefField}s
     */
    public <T> void map(final AbstractField dataSourceField,
                        final Field<T> field,
                        final Converter<T> converter) {
        expressionMapper.addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                MultiConverter.wrapConverter(converter)));
    }

    /**
     * Uses UUID or name for any {@link stroom.datasource.api.v2.DocRefField}s depending on useName
     */
    public <T> void map(final AbstractField dataSourceField,
                        final Field<T> field,
                        final Converter<T> converter,
                        final boolean useName) {
        expressionMapper.addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                MultiConverter.wrapConverter(converter),
                useName));
    }

    /**
     * Uses UUID for any {@link stroom.datasource.api.v2.DocRefField}s
     */
    public <T> void multiMap(final AbstractField dataSourceField,
                             final Field<T> field,
                             final MultiConverter<T> converter) {
        expressionMapper.addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                converter));
    }

    /**
     * Uses UUID or name for any {@link stroom.datasource.api.v2.DocRefField}s depending on useName
     */
    public <T> void multiMap(final AbstractField dataSourceField,
                             final Field<T> field,
                             final MultiConverter<T> converter,
                             final boolean useName) {
        expressionMapper.addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                converter,
                useName));
    }

    public void ignoreField(final AbstractField dataSourceField) {
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
                if (values == null) {
                    return Collections.emptyList();
                } else {
                    // Call the converter for each
                    return values.stream()
                            .flatMap(val -> converter.apply(val).stream())
                            .collect(Collectors.toList());
                }
            };
        }

        /**
         * Wraps a one to one {@link Converter} function inside a {@link MultiConverter}.
         */
        static <T> MultiConverter<T> wrapConverter(final Converter<T> converter) {
            return values -> {
                if (values == null) {
                    return Collections.emptyList();
                } else {
                    return values.stream()
                            .map(converter::apply)
                            .collect(Collectors.toList());
                }
            };
        }
    }
}
