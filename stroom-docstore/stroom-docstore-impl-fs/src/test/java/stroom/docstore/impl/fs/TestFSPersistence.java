package stroom.docstore.impl.fs;

import stroom.docref.DocRef;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.Doc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestFSPersistence {
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    @Test
    void test() throws IOException {
        final Persistence persistence = new FSPersistence(Files.createTempDirectory("docstore").resolve("conf"));

        final String uuid1 = UUID.randomUUID().toString();
        final String uuid2 = UUID.randomUUID().toString();
        final DocRef docRef = new DocRef("test-type", "test-uuid", "test-name");

        // Ensure the doc doesn't exist.
        if (persistence.exists(docRef)) {
            persistence.delete(docRef);
        }

        final GenericDoc doc = new GenericDoc();
        doc.setType(docRef.getType());
        doc.setUuid(docRef.getUuid());
        doc.setName(docRef.getName());
        ObjectMapper mapper = createMapper();
        byte[] bytes = mapper.writeValueAsBytes(doc);

        // Create
        Map<String, byte[]> data = new HashMap<>();
        data.put("meta", bytes);
        persistence.write(docRef, false, data);

        // Exists
        assertThat(persistence.exists(docRef)).isTrue();

        // Read
        data = persistence.read(docRef);
        assertThat(data.get("meta")).isEqualTo(bytes);

        // List
        List<DocRef> refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.get(0)).isEqualTo(docRef);
        assertThat(refs.get(0).getType()).isEqualTo(docRef.getType());
        assertThat(refs.get(0).getUuid()).isEqualTo(docRef.getUuid());
        assertThat(refs.get(0).getName()).isEqualTo(docRef.getName());

        // Update
        doc.setName("New Name");
        bytes = mapper.writeValueAsBytes(doc);
        data = new HashMap<>();
        data.put("meta", bytes);
        persistence.write(docRef, true, data);

        // Read
        data = persistence.read(docRef);
        assertThat(data.get("meta")).isEqualTo(bytes);

        // List
        refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.get(0)).isEqualTo(docRef);
        assertThat(refs.get(0).getType()).isEqualTo(docRef.getType());
        assertThat(refs.get(0).getUuid()).isEqualTo(docRef.getUuid());
        assertThat(refs.get(0).getName()).isEqualTo("New Name");

        // Delete
        persistence.delete(docRef);
    }

    private ObjectMapper createMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private static class GenericDoc extends Doc {
    }
}
