package stroom.docstore.shared;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.test.common.TestUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.UUID;
import java.util.stream.Stream;

class TestDocRefUtil {

    @Test
    void isSameDocument_same() {

        final String uuid = UUID.randomUUID().toString();
        final String type = "TEST_TYPE";

        final Doc doc = buildDoc(uuid, type);
        final DocRef docRef = DocRef.builder()
                .uuid(uuid)
                .type(type)
                .build();
        Assertions.assertThat(DocRefUtil.isSameDocument(docRef, doc))
                .isTrue();
        Assertions.assertThat(DocRefUtil.isSameDocument(doc, docRef))
                .isTrue();
    }

    @Test
    void isSameDocument_different1() {

        final String uuid = UUID.randomUUID().toString();
        final String type = "TEST_TYPE";

        final Doc doc = buildDoc(uuid, type);
        final DocRef docRef = DocRef.builder()
                .uuid(uuid)
                .type("foo")
                .build();
        Assertions.assertThat(DocRefUtil.isSameDocument(docRef, doc))
                .isFalse();
        Assertions.assertThat(DocRefUtil.isSameDocument(doc, docRef))
                .isFalse();
    }

    @Test
    void isSameDocument_different2() {

        final String uuid = UUID.randomUUID().toString();
        final String type = "TEST_TYPE";

        final Doc doc = buildDoc(uuid, type);
        final DocRef docRef = DocRef.builder()
                .uuid("foo")
                .type(type)
                .build();
        Assertions.assertThat(DocRefUtil.isSameDocument(docRef, doc))
                .isFalse();
        Assertions.assertThat(DocRefUtil.isSameDocument(doc, docRef))
                .isFalse();
    }

    @Test
    void name() {
        final String uuid = UUID.randomUUID().toString();
        final String type = "TEST_TYPE";
        final Doc doc = buildDoc(uuid, type);
        final DocRef docRef = DocRefUtil.create(doc);

        Assertions.assertThat(docRef.getName())
                .isEqualTo(doc.getName());
        Assertions.assertThat(docRef.getUuid())
                .isEqualTo(doc.getUuid());
        Assertions.assertThat(docRef.getType())
                .isEqualTo(doc.getType());
    }

    @TestFactory
    Stream<DynamicTest> testCreateSimpleDocRefString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(DocRef.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        DocRefUtil.createSimpleDocRefString(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(PipelineDoc.buildDocRef()
                                .uuid("3b573762-3f9b-4eaf-bb19-26344598abaf")
                                .build(),
                        "3b573762-3f9b-4eaf-bb19-26344598abaf")
                .addCase(PipelineDoc.buildDocRef()
                                .uuid("55ea911e-d1a2-4278-9bd7-8222b54c9f4b")
                                .name("foo")
                                .build(),
                        "foo {55ea911e-d1a2-4278-9bd7-8222b54c9f4b}")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreateTypedDocRefString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(DocRef.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        DocRefUtil.createTypedDocRefString(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(PipelineDoc.buildDocRef()
                                .uuid("3b573762-3f9b-4eaf-bb19-26344598abaf")
                                .build(),
                        "Pipeline 3b573762-3f9b-4eaf-bb19-26344598abaf")
                .addCase(PipelineDoc.buildDocRef()
                                .uuid("55ea911e-d1a2-4278-9bd7-8222b54c9f4b")
                                .name("foo")
                                .build(),
                        "Pipeline 'foo' {55ea911e-d1a2-4278-9bd7-8222b54c9f4b}")
                .build();
    }

    private Doc buildDoc(final String uuid, final String type) {
        return new Doc() {

            @Override
            public String getType() {
                return type;
            }

            @Override
            public String getUuid() {
                return uuid;
            }
        };
    }
}
