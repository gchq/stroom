package stroom.docstore.impl.fs;

import stroom.docref.DocRef;
import stroom.docstore.impl.DocumentData;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.AbstractDoc;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestFSPersistence {

    @Test
    void test() throws IOException {
        final Persistence persistence = new FSPersistence(
                Files.createTempDirectory("docstore").resolve("conf"));

        final GenericDoc doc = new GenericDoc();
        doc.setUuid("test-uuid");
        doc.setName("test-name");
        doc.setVersion(UUID.randomUUID().toString());
        final DocRef docRef = doc.asDocRef();

        // Ensure the doc doesn't exist.
        if (persistence.exists(docRef)) {
            persistence.delete(docRef);
        }

        ObjectMapper mapper = JsonUtil.getNoIndentMapper();
        byte[] bytes = mapper.writeValueAsBytes(doc);

        // Create
        final Map<String, byte[]> data = new HashMap<>();
        data.put("meta", bytes);
        final DocumentData documentData = DocumentData
                .builder()
                .docRef(docRef)
                .version(doc.getVersion())
                .uniqueName("test-type:test-uuid")
                .data(data)
                .build();
        persistence.create(documentData);

        // Exists
        assertThat(persistence.exists(docRef)).isTrue();

        // Read
        final Optional<DocumentData> optionalDocumentData = persistence.read(docRef);
        assertThat(optionalDocumentData).isPresent();
        final DocumentData saved = optionalDocumentData.get();
        assertThat(saved.getVersion()).isEqualTo(documentData.getVersion());
        final Map<String, byte[]> savedData = saved.getData();
        assertThat(savedData.get("meta")).isEqualTo(bytes);

        // List
        List<DocRef> refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        final DocRef docRef1 = refs.getFirst();
        assertThat(docRef1).isEqualTo(docRef);
        assertThat(docRef1.getType()).isEqualTo(docRef.getType());
        assertThat(docRef1.getUuid()).isEqualTo(docRef.getUuid());
        assertThat(docRef1.getName()).isEqualTo(docRef.getName());

        // Update
        doc.setVersion(UUID.randomUUID().toString());
        bytes = mapper.writeValueAsBytes(doc);
        final Map<String, byte[]> newData = new HashMap<>();
        newData.put("meta", bytes);
        final DocumentData newDocumentData = saved
                .copy()
                .version(doc.getVersion())
                .data(newData)
                .build();
        persistence.update(saved.getVersion(), newDocumentData);

        // Read
        final Optional<DocumentData> optionalDocumentData2 = persistence.read(docRef);
        assertThat(optionalDocumentData2).isPresent();
        final DocumentData saved2 = optionalDocumentData2.get();
        assertThat(saved2.getVersion()).isEqualTo(newDocumentData.getVersion());
        final Map<String, byte[]> savedData2 = saved2.getData();
        assertThat(savedData2.get("meta")).isEqualTo(bytes);

        // List
        refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        final DocRef docRef2 = refs.getFirst();
        assertThat(docRef2).isEqualTo(docRef);
        assertThat(docRef2.getType()).isEqualTo(docRef.getType());
        assertThat(docRef2.getUuid()).isEqualTo(docRef.getUuid());
        assertThat(docRef2.getName()).isEqualTo("test-name");

        // Delete
        persistence.delete(docRef);
    }

    private static class GenericDoc extends AbstractDoc {

        @Override
        public String getType() {
            return "GenericDoc";
        }
    }
}
