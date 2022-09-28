package stroom.docstore.shared;

import stroom.docref.DocRef;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
