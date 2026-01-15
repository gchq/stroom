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

package stroom.annotation.impl.db;

import stroom.annotation.shared.AddTag;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationFields;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.ChangeAssignedTo;
import stroom.annotation.shared.ChangeComment;
import stroom.annotation.shared.ChangeDescription;
import stroom.annotation.shared.ChangeRetentionPeriod;
import stroom.annotation.shared.ChangeSubject;
import stroom.annotation.shared.ChangeTitle;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.LinkEvents;
import stroom.annotation.shared.SetTag;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestAnnotationDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAnnotationDaoImpl.class);

    @Inject
    private AnnotationDaoImpl annotationDao;
    @Inject
    private AnnotationTagDaoImpl annotationTagDao;

    @BeforeEach
    void setup() {
        Guice.createInjector(new TestModule()).injectMembers(this);
        annotationTagDao.clear();
        annotationDao.clear();
    }

    @Test
    void testAnnotation() {
        final UserRef currentUser = new UserRef(
                "1234",
                "test",
                "test",
                "test",
                false,
                true);
        final AnnotationTag newStatus = createStatus("New");
        final AnnotationTag assignedStatus = createStatus("Assigned");
        final AnnotationTag closedStatus = createStatus("Closed");

        final CreateAnnotationRequest createAnnotationRequest = CreateAnnotationRequest
                .builder()
                .title("Test Title")
                .subject("Test Subject")
                .status("Assigned")
                .build();
        final Annotation annotation = annotationDao
                .createAnnotation(createAnnotationRequest, currentUser);

        final Optional<Annotation> optionalAnnotation = annotationDao
                .getAnnotationByDocRef(annotation.asDocRef());
        assertThat(optionalAnnotation).isPresent();
        assertThat(optionalAnnotation.get()).isEqualTo(annotation);
    }

    @Test
    void testAnnotationLabels() {
        final UserRef currentUser = new UserRef(
                "1234",
                "test",
                "test",
                "test",
                false,
                true);
        final AnnotationTag label1 = createLabel("Label1");
        final AnnotationTag label2 = createLabel("Label2");
        final AnnotationTag label3 = createLabel("Label3");

        final Annotation annotation1 = createAnnotation(currentUser);
        final Annotation annotation2 = createAnnotation(currentUser);

        addTag(currentUser, annotation1, label1);
        addTag(currentUser, annotation1, label2);
        addTag(currentUser, annotation2, label2);
        addTag(currentUser, annotation2, label3);

        testSearch(ExpressionOperator.builder().build(), List.of("Label1|Label2", "Label2|Label3"));

        // Test equals 1.
        testSearch(ExpressionOperator
                .builder()
                .addTerm(AnnotationFields.LABEL, Condition.EQUALS, "Label1")
                .build(), List.of("Label1|Label2"));

        // Test equals 2.
        testSearch(ExpressionOperator
                .builder()
                .addTerm(AnnotationFields.LABEL, Condition.EQUALS, "Label3")
                .build(), List.of("Label2|Label3"));

        // Test not equals 1.
        testSearch(ExpressionOperator
                .builder()
                .addTerm(AnnotationFields.LABEL, Condition.NOT_EQUALS, "Label1")
                .build(), List.of("Label2|Label3"));

        // Test not equals 2.
        testSearch(ExpressionOperator
                .builder()
                .addTerm(AnnotationFields.LABEL, Condition.NOT_EQUALS, "Label3")
                .build(), List.of("Label1|Label2"));

        // Test not equals 3.
        testSearch(ExpressionOperator
                .builder()
                .addTerm(AnnotationFields.LABEL, Condition.NOT_EQUALS, "Label2")
                .build(), List.of());
    }

    private void testSearch(final ExpressionOperator expressionOperator,
                            final List<String> expectedLabels) {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(AnnotationFields.ID);
        fieldIndex.create(AnnotationFields.LABEL);
        List<Val[]> list = new ArrayList<>();
        annotationDao.search(new ExpressionCriteria(
                expressionOperator), fieldIndex, list::add, uuid -> true);
        assertThat(list.size()).isEqualTo(expectedLabels.size());
        // Sort by id.
        list = list.stream().sorted(Comparator.comparing(vals -> vals[0].toLong())).toList();
        for (int i = 0; i < expectedLabels.size(); i++) {
            assertThat(list.get(i)[1].toString()).isEqualTo(expectedLabels.get(i));
        }
    }

    private Annotation createAnnotation(final UserRef currentUser) {
        final CreateAnnotationRequest createAnnotationRequest1 = CreateAnnotationRequest
                .builder()
                .title("Test Title")
                .subject("Test Subject")
                .build();
        return annotationDao
                .createAnnotation(createAnnotationRequest1, currentUser);
    }

    private void addTag(final UserRef currentUser, final Annotation annotation, final AnnotationTag tag) {
        final SingleAnnotationChangeRequest request =
                new SingleAnnotationChangeRequest(annotation.asDocRef(), new AddTag(tag));
        annotationDao.change(request, currentUser);
    }

    @Test
    void testAnnotationSearch() {
        final UserRef currentUser = new UserRef(
                "1234",
                "test",
                "test",
                "test",
                false,
                true);
        final AnnotationTag newStatus = createStatus("New");
        final AnnotationTag assignedStatus = createStatus("Assigned");
        final AnnotationTag closedStatus = createStatus("Closed");

        final AnnotationTag labelOne = createLabel("Label One");
        final AnnotationTag labelTwo = createLabel("Label Two");

        final AnnotationTag collectionOne = createCollection("Collection One");
        final AnnotationTag collectionTwo = createCollection("Collection Two");

        final CreateAnnotationRequest createAnnotationRequest = CreateAnnotationRequest
                .builder()
                .title("Test Title")
                .subject("Test Subject")
                .status("Assigned")
                .build();
        Annotation annotation = annotationDao.createAnnotation(createAnnotationRequest, currentUser);
        final DocRef docRef = annotation.asDocRef();
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new ChangeTitle("Test Title 2")),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new ChangeSubject("Test Subject 2")),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new ChangeAssignedTo(currentUser)),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new SetTag(assignedStatus)),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new AddTag(labelOne)),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new AddTag(labelTwo)),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new AddTag(collectionOne)),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new AddTag(collectionTwo)),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new ChangeComment("Comment 1")),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new ChangeComment("Comment 2")),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new ChangeComment("Comment 3")),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new ChangeRetentionPeriod(
                        new SimpleDuration(1, TimeUnit.YEARS))),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new ChangeDescription(
                        "Test Description")),
                currentUser);
        annotationDao.change(new SingleAnnotationChangeRequest(docRef, new LinkEvents(
                        List.of(new EventId(1, 1)))),
                currentUser);

        // Reload annotation so we have latest updated values.
        annotation = annotationDao.getAnnotationById(annotation.getId()).orElseThrow();

        final List<Val> expectedValues = List.of(
                ValString.create(annotation.getUuid()),
                ValDate.create(annotation.getCreateTimeMs()),
                ValString.create(annotation.getCreateUser()),
                ValDate.create(annotation.getUpdateTimeMs()),
                ValString.create(annotation.getUpdateUser()),
                ValString.create("Test Title 2"),
                ValString.create("Test Subject 2"),
                ValString.create("Assigned"),
                ValString.create("{1234}"),
                ValString.create("Label One|Label Two"),
                ValString.create("Collection One|Collection Two"),
                ValString.create("Comment 3"),
                ValString.create("Comment 1|Comment 2|Comment 3"),
                ValString.create("Test Description"),
                ValLong.create(1),
                ValLong.create(1),
                ValString.create("TEST_FEED_NAME"));

        // Try all fields first.
        final FieldIndex fieldIndex = new FieldIndex();
        final List<QueryField> filteredFields = AnnotationFields.FIELDS
                .stream()
                .filter(field -> !field.equals(AnnotationFields.ID_FIELD))
                .toList();

        filteredFields.forEach(field -> fieldIndex.create(field.getFldName()));
        final List<Val[]> list = new ArrayList<>();
        annotationDao.search(new ExpressionCriteria(), fieldIndex, list::add, uuid -> true);
        assertThat(list.size()).isOne();
        Val[] vals = list.getFirst();
        assertThat(vals.length).isEqualTo(expectedValues.size());
        for (int i = 0; i < vals.length; i++) {
            assertThat(vals[i]).isEqualTo(expectedValues.get(i));
        }

        // Test each field individually.
        for (int i = 0; i < filteredFields.size(); i++) {
            final QueryField field = filteredFields.get(i);
            final FieldIndex fieldIndex2 = new FieldIndex();
            fieldIndex2.create(field.getFldName());
            final List<Val[]> list2 = new ArrayList<>();
            annotationDao.search(new ExpressionCriteria(), fieldIndex2, list2::add, uuid -> true);
            assertThat(list2.size()).isOne();
            vals = list2.getFirst();
            assertThat(vals.length).isOne();
            assertThat(vals[0]).isEqualTo(expectedValues.get(i));
        }

        // Test each query term.
        final List<String> queryValues = List.of(
                annotation.getUuid(),
                ValDate.create(annotation.getCreateTimeMs()).toString(),
                annotation.getCreateUser(),
                ValDate.create(annotation.getUpdateTimeMs()).toString(),
                annotation.getUpdateUser(),
                "Test Title 2",
                "Test Subject 2",
                "Assigned",
                "1234",
                "Label One",
                "Collection One",
                "Comment 3",
                "Comment 1",
                "Test Description",
                "1",
                "1",
                "TEST_FEED_NAME");

        for (int i = 0; i < filteredFields.size() && i < queryValues.size(); i++) {
            final QueryField field = filteredFields.get(i);
            final FieldIndex fieldIndex2 = new FieldIndex();
            fieldIndex2.create(field.getFldName());
            final List<Val[]> list2 = new ArrayList<>();
            final String queryValue = queryValues.get(i);
            final ExpressionOperator expression = ExpressionOperator
                    .builder()
                    .addTerm(ExpressionTerm
                            .builder()
                            .field(field.getFldName())
                            .condition(Condition.EQUALS)
                            .value(queryValue)
                            .build())
                    .build();
            annotationDao.search(new ExpressionCriteria(expression), fieldIndex2, list2::add, uuid -> true);

            assertThat(list2.size())
                    .withFailMessage(() -> "Failed to run query against field: " + field.getFldName())
                    .isOne();
            if (!list2.isEmpty()) {
                vals = list2.getFirst();
                final Val val = vals[0];
                final Val expectedVal = expectedValues.get(i);
                assertThat(vals.length).isOne();
                assertThat(val)
                        .withFailMessage(() -> "Failed to run query against field: " + field.getFldName())
                        .isEqualTo(expectedVal);
            }
        }

        // Test each query term with no matching result field.
        for (int i = 0; i < filteredFields.size() && i < queryValues.size(); i++) {
            final QueryField field = filteredFields.get(i);
            final FieldIndex fieldIndex2 = new FieldIndex();
            fieldIndex2.create(AnnotationFields.UUID);
            final List<Val[]> list2 = new ArrayList<>();
            final String queryValue = queryValues.get(i);
            final ExpressionOperator expression = ExpressionOperator
                    .builder()
                    .addTerm(ExpressionTerm
                            .builder()
                            .field(field.getFldName())
                            .condition(Condition.EQUALS)
                            .value(queryValue)
                            .build())
                    .build();
            annotationDao.search(new ExpressionCriteria(expression), fieldIndex2, list2::add, uuid -> true);

            assertThat(list2.size())
                    .withFailMessage(() -> "Failed to run query against field: " + field.getFldName())
                    .isOne();
            if (!list2.isEmpty()) {
                vals = list2.getFirst();
                final Val val = vals[0];
                final Val expectedVal = ValString.create(annotation.getUuid());
                assertThat(vals.length).isOne();
                assertThat(val)
                        .withFailMessage(() -> "Failed to run query against field: " + field.getFldName())
                        .isEqualTo(expectedVal);
            }
        }
    }

    private AnnotationTag createStatus(final String name) {
        return annotationTagDao.createAnnotationTag(CreateAnnotationTagRequest
                .builder()
                .type(AnnotationTagType.STATUS)
                .name(name)
                .build());
    }

    private AnnotationTag createCollection(final String name) {
        return annotationTagDao.createAnnotationTag(CreateAnnotationTagRequest
                .builder()
                .type(AnnotationTagType.COLLECTION)
                .name(name)
                .build());
    }

    private AnnotationTag createLabel(final String name) {
        return annotationTagDao.createAnnotationTag(CreateAnnotationTagRequest
                .builder()
                .type(AnnotationTagType.LABEL)
                .name(name)
                .build());
    }
}
