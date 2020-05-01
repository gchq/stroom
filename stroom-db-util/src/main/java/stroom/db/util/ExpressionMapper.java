package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.CommonExpressionMapper.TermHandler;
import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionItem;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ExpressionMapper implements Function<ExpressionItem, Collection<Condition>> {
    private final CommonExpressionMapper expressionMapper;
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;

    ExpressionMapper(final WordListProvider wordListProvider,
                     final CollectionService collectionService) {
        expressionMapper = new CommonExpressionMapper(false);
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
    }

    public <T> void map(final AbstractField dataSourceField, final Field<T> field, final Converter<T> converter) {
        expressionMapper.addHandler(dataSourceField, new TermHandler<>(dataSourceField, field, new ConverterAdapter<>(converter), wordListProvider, collectionService));
    }

    public <T> void multiMap(final AbstractField dataSourceField, final Field<T> field, final MultiConverter<T> converter) {
        expressionMapper.addHandler(dataSourceField, new TermHandler<>(dataSourceField, field, converter, wordListProvider, collectionService));
    }

    public <T> void multiMap(final AbstractField dataSourceField, final Field<T> field, final MultiConverter<T> converter, final boolean useName) {
        expressionMapper.addHandler(dataSourceField, new TermHandler<>(dataSourceField, field, converter, wordListProvider, collectionService, useName));
    }

    public void ignoreField(final AbstractField dataSourceField) {
        expressionMapper.ignoreField(dataSourceField);
    }

    @Override
    public Collection<Condition> apply(final ExpressionItem expressionItem) {
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
