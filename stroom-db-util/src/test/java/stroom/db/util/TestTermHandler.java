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

import stroom.collection.api.CollectionService;
import stroom.db.util.ExpressionMapper.MultiConverter;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.QueryField;
import stroom.test.common.TestUtil;

import com.google.inject.Provider;
import io.vavr.Tuple;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class TestTermHandler {

    private static final QueryField DOC_REF_FIELD = MetaFields.FEED;
    private static final QueryField TEXT_FIELD = MetaFields.TYPE;
    private static final QueryField ID_FIELD = MetaFields.ID;

    private static final Field<String> DOC_REF_DB_FIELD = DSL.field(DOC_REF_FIELD.getFldName(), String.class);
    private static final Field<String> TEXT_DB_FIELD = DSL.field(TEXT_FIELD.getFldName(), String.class);
    private static final Field<Long> ID_DB_FIELD = DSL.field(ID_FIELD.getFldName(), Long.class);
    private static final DocRef A_DOC_REF = DocRef.builder()
            .type(DOC_REF_FIELD.getDocRefType())
            .uuid("MyUUID")
            .name("MyName")
            .build();

    private static final DocRef A_FOLDER_DOC_REF = DocRef.builder()
            .type("Folder")
            .uuid("MyFolderUUID")
            .name("MyFolderName")
            .build();

    @Mock
    private WordListProvider wordListProviderMock;
    @Mock
    private CollectionService collectionServiceMock;
    @Mock
    private DocRefInfoService docRefInfoServiceMock;

    @Test
    void name() {
        final TermHandler<String> termHandler = getDocRefTermHandler(false);
//        termHandler.apply(E)
    }

    @TestFactory
    Stream<DynamicTest> testIdField() {
        Mockito.when(wordListProviderMock.getWords(Mockito.any()))
                .thenReturn(new String[]{"1", "2", "3", "4"});

        final TermHandler<Long> termHandler = getIdTermHandler(false);
        return TestUtil.buildDynamicTestStream()
                .withInputType(ExpressionTerm.class)
                .withOutputType(Condition.class)
                .withTestFunction(testCase ->
                        termHandler.apply(testCase.getInput()))
                .withSimpleEqualityAssertion()

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.EQUALS)
                                .value("123")
                                .build(),
                        ID_DB_FIELD.eq(123L))

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.EQUALS)
                                .value("")
                                .build(),
                        ID_DB_FIELD.isNull()) // Empty value

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.EQUALS)
                                .build(),
                        ID_DB_FIELD.isNull()) // Empty value

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.BETWEEN)
                                .value("1,3")
                                .build(),
                        ID_DB_FIELD.between(1L, 3L))

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.IS_NULL)
                                .build(),
                        ID_DB_FIELD.isNull())

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.IS_NOT_NULL)
                                .build(),
                        ID_DB_FIELD.isNotNull())

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.LESS_THAN)
                                .value("123")
                                .build(),
                        ID_DB_FIELD.lessThan(123L))

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO)
                                .value("123")
                                .build(),
                        ID_DB_FIELD.lessOrEqual(123L))

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.GREATER_THAN)
                                .value("123")
                                .build(),
                        ID_DB_FIELD.greaterThan(123L))

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO)
                                .value("123")
                                .build(),
                        ID_DB_FIELD.greaterOrEqual(123L))

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.IN)
                                .value("1,2,3,4")
                                .build(),
                        ID_DB_FIELD.in(1L, 2L, 3L, 4L))

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.IN)
                                .value("1")
                                .build(),
                        ID_DB_FIELD.in(1L))

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.IN)
                                .value("")
                                .build(),
                        DSL.falseCondition()) // Empty in list, so always false

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.IN)
                                .build(),
                        DSL.falseCondition())

                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.IN_DICTIONARY)
                                .value("1,2,3,4")
                                .docRef(A_DOC_REF)
                                .build(),
                        ID_DB_FIELD.in(1L, 2L, 3L, 4L))

                // An un-supported field, so ought to return DSL.falseCondition
                // though that is the same as 'in ()'
                .addCase(ExpressionTerm.builder()
                                .field(ID_FIELD.getFldName())
                                .condition(ExpressionTerm.Condition.IN_FOLDER)
                                .value("1")
                                .build(),
                        ID_DB_FIELD.in(Collections.emptyList()))

                .build();
    }

    @TestFactory
    Stream<DynamicTest> testDocRefField() {
        Mockito.when(docRefInfoServiceMock.name(Mockito.eq(A_DOC_REF)))
                .thenReturn(Optional.of(A_DOC_REF.getName()));

        Mockito.when(collectionServiceMock.getDescendants(
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(Set.of(A_DOC_REF));

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(ExpressionTerm.class, boolean.class)
                .withOutputType(Condition.class)
                .withTestFunction(testCase -> {
                    final ExpressionTerm term = testCase.getInput()._1;
                    final Boolean useValues = testCase.getInput()._2;
                    final TermHandler<String> termHandler = getDocRefTermHandler(useValues);
                    return termHandler.apply(term);
                })
                .withSimpleEqualityAssertion()

                .addCase(Tuple.of(
                                ExpressionTerm.builder()
                                        .field(DOC_REF_FIELD.getFldName())
                                        .condition(ExpressionTerm.Condition.EQUALS)
                                        .value(A_DOC_REF.getUuid())
                                        .build(),
                                false),
                        DOC_REF_DB_FIELD.eq(A_DOC_REF.getUuid()))

                .addCase(Tuple.of(
                                ExpressionTerm.builder()
                                        .field(DOC_REF_FIELD.getFldName())
                                        .condition(ExpressionTerm.Condition.IS_DOC_REF)
                                        .docRef(A_DOC_REF)
                                        .build(),
                                true),
                        DOC_REF_DB_FIELD.eq(A_DOC_REF.getName()))

                .addCase(Tuple.of(
                                ExpressionTerm.builder()
                                        .field(DOC_REF_FIELD.getFldName())
                                        .condition(ExpressionTerm.Condition.IS_DOC_REF)
                                        .docRef(A_DOC_REF)
                                        .build(),
                                false),
                        DOC_REF_DB_FIELD.eq(A_DOC_REF.getUuid()))

                .addCase(Tuple.of(
                                ExpressionTerm.builder()
                                        .field(DOC_REF_FIELD.getFldName())
                                        .condition(ExpressionTerm.Condition.IN_FOLDER)
                                        .docRef(A_FOLDER_DOC_REF)
                                        .build(),
                                true),
                        DOC_REF_DB_FIELD.in(A_DOC_REF.getName()))
                .build();
    }


    TermHandler<String> getDocRefTermHandler(final boolean useName) {
        return new TermHandler<>(
                DOC_REF_FIELD,
                DSL.field(DOC_REF_FIELD.getFldName(), String.class),
                values -> values,
                new MockProviderImpl<>(wordListProviderMock),
                new MockProviderImpl<>(collectionServiceMock),
                new MockProviderImpl<>(docRefInfoServiceMock),
                useName,
                false);
    }

    TermHandler<Long> getIdTermHandler(final boolean useName) {
        return new TermHandler<>(
                ID_FIELD,
                DSL.field(ID_FIELD.getFldName(), Long.class),
                MultiConverter.wrapConverter(Long::parseLong),
                new MockProviderImpl<>(wordListProviderMock),
                new MockProviderImpl<>(collectionServiceMock),
                new MockProviderImpl<>(docRefInfoServiceMock),
                useName,
                false);
    }

    /**
     * Mock Provider to wrap an object with a Provider so we can pass it into the StoreImpl.
     * Not a true provider as it always returns the same object.
     */
    private static class MockProviderImpl<T> implements Provider<T> {
        private final T provided;

        public MockProviderImpl(final T provided) {
            this.provided = provided;
        }

        @Override
        public T get() {
            return provided;
        }
    }
}
