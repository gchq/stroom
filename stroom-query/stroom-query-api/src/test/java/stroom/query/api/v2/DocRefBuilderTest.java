package stroom.query.api.v2;

import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocRefBuilderTest {

    @Test
    void doesBuild() {
        final String name = "someName";
        final String type = "someType";
        final String uuid = UUID.randomUUID().toString();

        final DocRef docRef = DocRef
                .builder()
                .name(name)
                .type(type)
                .uuid(uuid)
                .build();

        assertThat(docRef.getName()).isEqualTo(name);
        assertThat(docRef.getType()).isEqualTo(type);
        assertThat(docRef.getUuid()).isEqualTo(uuid);
    }
}
