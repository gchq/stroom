/*
 * Copyright 2025 Crown Copyright
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

package stroom.annotation.impl.dao;

import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.annotation.shared.FindAnnotationTagCriteria;
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

    private static final String FILTER_STATUS = "new";
    private static final String FILTER_LABEL = "one";
    private static final String FILTER_COLLECTION = "one";

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

        // The type is a field of the criteria now, not a term - a filter cannot reach it.
        FindAnnotationTagCriteria criteria =
                new FindAnnotationTagCriteria(AnnotationTagType.STATUS, FILTER_STATUS);
        ResultPage<AnnotationTag> resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isOne();

        criteria = new FindAnnotationTagCriteria(AnnotationTagType.STATUS);
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

        // The type is a field of the criteria now, not a term - a filter cannot reach it.
        FindAnnotationTagCriteria criteria =
                new FindAnnotationTagCriteria(AnnotationTagType.COLLECTION, FILTER_COLLECTION);
        ResultPage<AnnotationTag> resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isOne();

        criteria = new FindAnnotationTagCriteria(AnnotationTagType.COLLECTION);
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

        // The type is a field of the criteria now, not a term - a filter cannot reach it.
        FindAnnotationTagCriteria criteria =
                new FindAnnotationTagCriteria(AnnotationTagType.LABEL, FILTER_LABEL);
        ResultPage<AnnotationTag> resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isOne();

        criteria = new FindAnnotationTagCriteria(AnnotationTagType.LABEL);
        resultPage = annotationTagDao.findAnnotationTags(criteria);
        assertThat(resultPage.size()).isEqualTo(2);

        annotationTagDao.deleteAnnotationTag(one);
        annotationTagDao.deleteAnnotationTag(two);
    }

    /**
     * The type says what kind of tag is being looked for, so a quick filter must only ever narrow
     * within it. If the type were an expression term the user could type "typeid:label" and change
     * what the chooser is showing, which is why it is a field of the criteria instead and why
     * TYPE_ID is absent from AnnotationTagFields.QUICK_FILTER_FIELDS.
     */
    @Test
    void testQuickFilterCannotReachOutsideItsType() {
        final AnnotationTag status = createStatus("Shared");
        final AnnotationTag label = createLabel("Shared");

        // Both types have a tag of this name, but each chooser only ever sees its own.
        assertThat(annotationTagDao.findAnnotationTags(
                new FindAnnotationTagCriteria(AnnotationTagType.STATUS, "Shared")).size()).isOne();
        assertThat(annotationTagDao.findAnnotationTags(
                new FindAnnotationTagCriteria(AnnotationTagType.LABEL, "Shared")).size()).isOne();

        // Naming the type as a qualifier does not widen the result - "typeid" resolves to no
        // field, so the whole thing is an ordinary value that matches no tag name.
        assertThat(annotationTagDao.findAnnotationTags(
                new FindAnnotationTagCriteria(AnnotationTagType.STATUS, "typeid:Label")).size())
                .isZero();

        annotationTagDao.deleteAnnotationTag(status);
        annotationTagDao.deleteAnnotationTag(label);
    }

    /**
     * The chooser filters now speak the full quick filter grammar, where before they were a single
     * hardcoded CONTAINS built on the client.
     */
    @Test
    void testQuickFilterSupportsTheFullGrammar() {
        final AnnotationTag alpha = createStatus("Alpha");
        final AnnotationTag beta = createStatus("Beta");

        assertThat(find("Alpha")).isOne();
        assertThat(find("^Al")).describedAs("starts with").isOne();
        assertThat(find("$ta")).describedAs("ends with").isOne();
        assertThat(find("=Alpha")).describedAs("exact").isOne();
        assertThat(find("/A.*a")).describedAs("regex").isOne();
        assertThat(find("!Alpha")).describedAs("negated").isOne();
        assertThat(find("Alpha or Beta")).describedAs("or").isEqualTo(2);
        assertThat(find("name:Alpha")).describedAs("qualified").isOne();

        annotationTagDao.deleteAnnotationTag(alpha);
        annotationTagDao.deleteAnnotationTag(beta);
    }

    /**
     * A filter naming a condition the field cannot honour comes back empty with the reason, not as
     * an error and not as a silent empty grid. See ResultPage.filterError.
     */
    @Test
    void testRejectedQuickFilterExplainsItself() {
        final AnnotationTag alpha = createStatus("Alpha");

        final ResultPage<AnnotationTag> page = annotationTagDao.findAnnotationTags(
                new FindAnnotationTagCriteria(AnnotationTagType.STATUS, "Alpha and"));

        assertThat(page.getValues()).isEmpty();
        assertThat(page.getFilterError()).isNotNull();
        assertThat(page.getFilterError().getText()).isNotBlank();

        // ...and an ordinary empty result carries no diagnostic.
        final ResultPage<AnnotationTag> noMatch = annotationTagDao.findAnnotationTags(
                new FindAnnotationTagCriteria(AnnotationTagType.STATUS, "NoSuchTag"));
        assertThat(noMatch.getValues()).isEmpty();
        assertThat(noMatch.getFilterError()).isNull();

        annotationTagDao.deleteAnnotationTag(alpha);
    }

    private int find(final String quickFilter) {
        return annotationTagDao.findAnnotationTags(
                new FindAnnotationTagCriteria(AnnotationTagType.STATUS, quickFilter)).size();
    }

    @Test
    void testCreateTagAlreadyExists() {
        final AnnotationTag original = createStatus("New");
        annotationTagDao.deleteAnnotationTag(original);

        // Should not be findable after deletion.
        assertThat(annotationTagDao.findAnnotationTag(AnnotationTagType.STATUS, "New")).isEmpty();

        // Re-creating with the same type and name should undelete the original.
        final AnnotationTag recreated = createStatus("New");
        assertThat(recreated.getId()).isEqualTo(original.getId());
        assertThat(recreated.getUuid()).isEqualTo(original.getUuid());

        // Should now be findable again.
        assertThat(annotationTagDao.findAnnotationTag(AnnotationTagType.STATUS, "New")).isPresent();
    }

    @Test
    void testCreateCommentTagAlreadyExistsUpdatesTagText() {
        final AnnotationTag original = createComment("Phishing", "This looks like a phishing attempt.");
        annotationTagDao.deleteAnnotationTag(original);

        // Should not be findable after deletion.
        assertThat(annotationTagDao.findAnnotationTag(AnnotationTagType.COMMENT, "Phishing")).isEmpty();

        // Re-creating with updated tagText should undelete and update the text.
        final AnnotationTag recreated = createComment("Phishing", "Updated phishing comment text.");
        assertThat(recreated.getId()).isEqualTo(original.getId());
        assertThat(recreated.getUuid()).isEqualTo(original.getUuid());
        assertThat(recreated.getTagText()).isEqualTo("Updated phishing comment text.");

        // Should now be findable with updated tagText.
        final Optional<AnnotationTag> found = annotationTagDao
                .findAnnotationTag(AnnotationTagType.COMMENT, "Phishing");
        assertThat(found).isPresent();
        assertThat(found.get().getTagText()).isEqualTo("Updated phishing comment text.");
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

    private AnnotationTag createComment(final String name, final String tagText) {
        return annotationTagDao.createAnnotationTag(CreateAnnotationTagRequest
                .builder()
                .type(AnnotationTagType.COMMENT)
                .name(name)
                .tagText(tagText)
                .build());
    }
}
