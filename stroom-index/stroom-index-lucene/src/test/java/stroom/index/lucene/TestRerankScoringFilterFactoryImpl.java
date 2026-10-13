/*
 * Copyright 2026 Crown Copyright
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

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.RerankScoringFilter;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.ErrorMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestRerankScoringFilterFactoryImpl {

    private static final String SCORE_FIELD_SUFFIX = "__rerank_score";
    private static final String VALUE_FIELD_SUFFIX = "__rerank_value";
    private static final String VECTOR_FIELD = "myVector";

    @Mock
    private IndexFieldCache mockIndexFieldCache;
    @Mock
    private OpenAIModelStore mockOpenAIModelStore;
    @Mock
    private AiService mockAiService;
    @Mock
    private ValuesConsumer mockValuesConsumer;

    private RerankScoringFilterFactoryImpl factory;
    private ErrorConsumerImpl errorConsumer;

    @BeforeEach
    void setUp() {
        factory = new RerankScoringFilterFactoryImpl(
                mockIndexFieldCache, mockOpenAIModelStore, mockAiService);
        errorConsumer = new ErrorConsumerImpl();
    }

    @Test
    void testNoDenseVectorFields_noFilterAndNoError() {
        final Optional<RerankScoringFilter> result = create(fieldIndex("Feed", "EventTime"));

        assertThat(result).isEmpty();
        assertThat(errorMessages()).isEmpty();
        verifyNoInteractions(mockIndexFieldCache);
    }

    @Test
    void testValueFieldWithoutScoreField_isIgnoredSilently() {
        // The value field is populated by extraction, not by this filter, so selecting it on its
        // own needs no reranking and must not be reported as a problem.
        final Optional<RerankScoringFilter> result =
                create(fieldIndex(VECTOR_FIELD + VALUE_FIELD_SUFFIX));

        assertThat(result).isEmpty();
        assertThat(errorMessages()).isEmpty();
    }

    @Test
    void testScoreFieldWithoutValueField_reportsAUsefulError() {
        // Only this filter ever populates a score field, so without a value field to rerank the
        // column can never be filled in. Staying silent would leave the user with an empty column
        // and no explanation, which is what issue #5773 was about.
        final Optional<RerankScoringFilter> result =
                create(fieldIndex(VECTOR_FIELD + SCORE_FIELD_SUFFIX));

        assertThat(result).isEmpty();
        assertThat(errorMessages()).hasSize(1);

        final String message = errorMessages().getFirst();
        assertThat(message)
                .describedAs("The message must name both the offending field and the missing one")
                .contains(VECTOR_FIELD + SCORE_FIELD_SUFFIX)
                .contains(VECTOR_FIELD + VALUE_FIELD_SUFFIX);
        assertThat(message)
                .describedAs("The message must not be a raw NullPointerException")
                .doesNotContain("null");
    }

    @Test
    void testScoreFieldWithoutValueField_errorNamesTheRightFieldWhenOthersArePresent() {
        final Optional<RerankScoringFilter> result = create(fieldIndex(
                "Feed",
                "otherVector" + VALUE_FIELD_SUFFIX,
                VECTOR_FIELD + SCORE_FIELD_SUFFIX));

        assertThat(result).isEmpty();
        assertThat(errorMessages()).hasSize(1);
        assertThat(errorMessages().getFirst())
                .contains(VECTOR_FIELD + SCORE_FIELD_SUFFIX)
                .doesNotContain("otherVector");
    }

    @Test
    void testCompletePair_isChosenOverAnIncompleteOne() {
        // A complete pair must be chosen even when an incomplete value-only field comes first.
        // Reaching the index field lookup proves the pair was selected; the lookup returns null
        // here so we can assert on which field name it went looking for.
        final Optional<RerankScoringFilter> result = create(fieldIndex(
                "otherVector" + VALUE_FIELD_SUFFIX,
                VECTOR_FIELD + VALUE_FIELD_SUFFIX,
                VECTOR_FIELD + SCORE_FIELD_SUFFIX));

        assertThat(result).isEmpty();
        assertThat(errorMessages()).hasSize(1);
        assertThat(errorMessages().getFirst())
                .describedAs("Should have gone on to look up the complete pair's vector field")
                .contains("Unable to find dense vector field")
                .contains(VECTOR_FIELD);
    }

    @Test
    void testStrayScoreFieldDoesNotStopAValidPairBeingReranked() {
        // A leftover score-only column for one field must not disable reranking for another field
        // that is properly specified. Reaching the index field lookup proves we still went on to
        // rerank the good pair, and the stray is reported alongside it rather than aborting.
        final Optional<RerankScoringFilter> result = create(fieldIndex(
                VECTOR_FIELD + VALUE_FIELD_SUFFIX,
                VECTOR_FIELD + SCORE_FIELD_SUFFIX,
                "strayVector" + SCORE_FIELD_SUFFIX));

        assertThat(result).isEmpty();
        assertThat(errorMessages())
                .describedAs("Expected the stray to be reported and the good pair still attempted")
                .hasSize(2);
        assertThat(errorMessages())
                .anySatisfy(message -> assertThat(message)
                        .contains("strayVector" + SCORE_FIELD_SUFFIX)
                        .contains("strayVector" + VALUE_FIELD_SUFFIX));
        assertThat(errorMessages())
                .anySatisfy(message -> assertThat(message)
                        .contains("Unable to find dense vector field")
                        .contains(VECTOR_FIELD));
    }

    @Test
    void testTwoCompletePairs_firstDeclaredWinsAndTheOtherIsReported() {
        // Only one dense vector field can be reranked per query. Which one wins must not depend on
        // hash ordering, and the one that loses must be reported rather than silently left empty.
        final Optional<RerankScoringFilter> result = create(fieldIndex(
                "aaaVector" + VALUE_FIELD_SUFFIX,
                "aaaVector" + SCORE_FIELD_SUFFIX,
                "zzzVector" + VALUE_FIELD_SUFFIX,
                "zzzVector" + SCORE_FIELD_SUFFIX));

        assertThat(result).isEmpty();
        assertThat(errorMessages()).hasSize(2);
        assertThat(errorMessages())
                .describedAs("The first declared pair must be the one that is reranked")
                .anySatisfy(message -> assertThat(message)
                        .contains("Unable to find dense vector field")
                        .contains("aaaVector"));
        assertThat(errorMessages())
                .describedAs("The pair that was not used must be named")
                .anySatisfy(message -> assertThat(message)
                        .contains("zzzVector" + SCORE_FIELD_SUFFIX)
                        .contains("will not be scored"));
    }

    private Optional<RerankScoringFilter> create(final FieldIndex fieldIndex) {
        return factory.create(
                DocRef.builder().type("Index").uuid("test-uuid").name("Test Index").build(),
                ExpressionOperator.builder().build(),
                fieldIndex,
                mockValuesConsumer,
                errorConsumer);
    }

    private List<String> errorMessages() {
        return errorConsumer.getErrorMessages()
                .stream()
                .map(ErrorMessage::getMessage)
                .toList();
    }

    private static FieldIndex fieldIndex(final String... fieldNames) {
        final FieldIndex fieldIndex = new FieldIndex();
        for (final String fieldName : fieldNames) {
            fieldIndex.create(fieldName);
        }
        return fieldIndex;
    }
}
