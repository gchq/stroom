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

import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagFields;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestAnnotationTagDaoImpl {

    @Inject
    private AnnotationTagDaoImpl annotationTagDao;

    @BeforeEach
    void setup() {
        Guice.createInjector(new TestModule()).injectMembers(this);
        annotationTagDao.clear();
    }

    @Test
    void testStatus() {
        final AnnotationTag newStatus = createStatus("New");
        final AnnotationTag assignedStatus = createStatus("Assigned");
        final AnnotationTag closedStatus = createStatus("Closed");

        final Optional<AnnotationTag> optionalAnnotationTag = annotationTagDao
                .findAnnotationTag(AnnotationTagType.STATUS, "New");
        assertThat(optionalAnnotationTag).isPresent();
        assertThat(optionalAnnotationTag.get()).isEqualTo(newStatus);

        ExpressionOperator expression = ExpressionOperator
                .builder()
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.TYPE_ID)
                        .condition(Condition.EQUALS)
                        .value(AnnotationTagType.STATUS.getDisplayValue())
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.NAME)
                        .condition(Condition.CONTAINS)
                        .value("new")
                        .build())
                .build();
        ExpressionCriteria criteria = new ExpressionCriteria(expression);
        ResultPage<AnnotationTag> resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isOne();

        expression = ExpressionOperator
                .builder()
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.TYPE_ID)
                        .condition(Condition.EQUALS)
                        .value(AnnotationTagType.STATUS.getDisplayValue())
                        .build())
                .build();
        criteria = new ExpressionCriteria(expression);
        resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isEqualTo(3);

        annotationTagDao.deleteAnnotationTag(newStatus);
        annotationTagDao.deleteAnnotationTag(assignedStatus);
        annotationTagDao.deleteAnnotationTag(closedStatus);
    }

    @Test
    void testCollections() {
        final AnnotationTag one = createCollection("One");
        final AnnotationTag two = createCollection("Two");

        final Optional<AnnotationTag> optionalAnnotationTag = annotationTagDao
                .findAnnotationTag(AnnotationTagType.COLLECTION, "one");
        assertThat(optionalAnnotationTag).isPresent();
        assertThat(optionalAnnotationTag.get()).isEqualTo(one);

        ExpressionOperator expression = ExpressionOperator
                .builder()
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.TYPE_ID)
                        .condition(Condition.EQUALS)
                        .value(AnnotationTagType.COLLECTION.getDisplayValue())
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.NAME)
                        .condition(Condition.CONTAINS)
                        .value("one")
                        .build())
                .build();
        ExpressionCriteria criteria = new ExpressionCriteria(expression);
        ResultPage<AnnotationTag> resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isOne();

        expression = ExpressionOperator
                .builder()
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.TYPE_ID)
                        .condition(Condition.EQUALS)
                        .value(AnnotationTagType.COLLECTION.getDisplayValue())
                        .build())
                .build();
        criteria = new ExpressionCriteria(expression);
        resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isEqualTo(2);

        annotationTagDao.deleteAnnotationTag(one);
        annotationTagDao.deleteAnnotationTag(two);
    }

    @Test
    void testLabels() {
        final AnnotationTag one = createLabel("One");
        final AnnotationTag two = createLabel("Two");

        final Optional<AnnotationTag> optionalAnnotationTag = annotationTagDao
                .findAnnotationTag(AnnotationTagType.LABEL, "one");
        assertThat(optionalAnnotationTag).isPresent();
        assertThat(optionalAnnotationTag.get()).isEqualTo(one);

        ExpressionOperator expression = ExpressionOperator
                .builder()
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.TYPE_ID)
                        .condition(Condition.EQUALS)
                        .value(AnnotationTagType.LABEL.getDisplayValue())
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.NAME)
                        .condition(Condition.CONTAINS)
                        .value("one")
                        .build())
                .build();
        ExpressionCriteria criteria = new ExpressionCriteria(expression);
        ResultPage<AnnotationTag> resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isOne();

        expression = ExpressionOperator
                .builder()
                .addTerm(ExpressionTerm.builder()
                        .field(AnnotationTagFields.TYPE_ID)
                        .condition(Condition.EQUALS)
                        .value(AnnotationTagType.LABEL.getDisplayValue())
                        .build())
                .build();
        criteria = new ExpressionCriteria(expression);
        resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isEqualTo(2);

        annotationTagDao.deleteAnnotationTag(one);
        annotationTagDao.deleteAnnotationTag(two);
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
