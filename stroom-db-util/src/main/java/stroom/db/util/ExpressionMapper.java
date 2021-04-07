package stroom.db.util;

import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionItem;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ExpressionMapper implements Function<ExpressionItem, Condition> {

    private final CommonExpressionMapper expressionMapper;
    private final TermHandlerFactory termHandlerFactory;

    ExpressionMapper(final TermHandlerFactory termHandlerFactory,
                     final Function<ExpressionItem, Condition> delegateItemHandler) {
        expressionMapper = new CommonExpressionMapper(delegateItemHandler);
        this.termHandlerFactory = termHandlerFactory;
    }

    public <T> void map(final AbstractField dataSourceField,
                        final Field<T> field, final Converter<T> converter) {
        expressionMapper.addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                new ConverterAdapter<>(converter)));
    }

    public <T> void map(final AbstractField dataSourceField,
                        final Field<T> field,
                        final Converter<T> converter, final boolean useName) {
        expressionMapper.addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                new ConverterAdapter<>(converter),
                useName));
    }

    public <T> void multiMap(final AbstractField dataSourceField,
                             final Field<T> field,
                             final MultiConverter<T> converter) {
        expressionMapper.addHandler(dataSourceField, termHandlerFactory.create(
                dataSourceField,
                field,
                converter));
    }

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

    public interface Converter<T> {

        T apply(String value);
    }

    public interface MultiConverter<T> {

        List<T> apply(String value);
    }

    private static class ConverterAdapter<T> implements MultiConverter<T> {

        private final Converter<T> converter;

        ConverterAdapter(final Converter<T> converter) {
            this.converter = converter;
        }

        @Override
        public List<T> apply(final String value) {
            final T t = converter.apply(value);
            if (t != null) {
                return List.of(t);
            }
            return Collections.emptyList();
        }
    }
}
