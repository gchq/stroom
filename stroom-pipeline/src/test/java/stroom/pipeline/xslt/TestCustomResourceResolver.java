package stroom.pipeline.xslt;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestCustomResourceResolver {
    @Test
    void testParseDocRef() {
        DocRef docRef = CustomResourceResolver.parseDocRef("test-uuid");
        assertThat(docRef.getUuid()).isEqualTo("test-uuid");
        assertThat(docRef.getName()).isNull();

        docRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "test-uuid", "test-name");
        final DocRef parsed = CustomResourceResolver.parseDocRef(docRef.toString());
        assertThat(parsed).isEqualTo(docRef);
    }

    @Test
    void testGetPart() {
        final DocRef docRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "test-uuid", "test-name");
        String docRefString = docRef.toString();
        docRefString = docRefString.replaceAll("\"", "'");

        String value = CustomResourceResolver.getPart("type", docRefString, null);
        assertThat(value).isEqualTo(PipelineDoc.DOCUMENT_TYPE);

        value = CustomResourceResolver.getPart("uuid", docRefString, null);
        assertThat(value).isEqualTo("test-uuid");

        value = CustomResourceResolver.getPart("name", docRefString, null);
        assertThat(value).isEqualTo("test-name");

        docRefString = docRefString.replaceAll("'", "\"");

        value = CustomResourceResolver.getPart("type", docRefString, null);
        assertThat(value).isEqualTo(PipelineDoc.DOCUMENT_TYPE);

        value = CustomResourceResolver.getPart("uuid", docRefString, null);
        assertThat(value).isEqualTo("test-uuid");

        value = CustomResourceResolver.getPart("name", docRefString, null);
        assertThat(value).isEqualTo("test-name");

        docRefString = "type=Pipeline, uuid=test-uuid, name=test-name";

        value = CustomResourceResolver.getPart("type", docRefString, null);
        assertThat(value).isEqualTo(PipelineDoc.DOCUMENT_TYPE);

        value = CustomResourceResolver.getPart("uuid", docRefString, null);
        assertThat(value).isEqualTo("test-uuid");

        value = CustomResourceResolver.getPart("name", docRefString, null);
        assertThat(value).isEqualTo("test-name");
    }
}
