package stroom.docstore.impl.fs;

import stroom.docref.DocRef;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.AbstractDoc;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestFSPersistence {

    @Test
    void test() throws IOException {
        final Persistence persistence = new FSPersistence(
                Files.createTempDirectory("docstore").resolve("conf"));

        final String uuid1 = UUID.randomUUID().toString();
        final String uuid2 = UUID.randomUUID().toString();
        final DocRef docRef = new DocRef("test-type", "test-uuid", "test-name");

        // Ensure the doc doesn't exist.
        if (persistence.exists(docRef)) {
            persistence.delete(docRef);
        }

        final GenericDoc doc = new GenericDoc(
                docRef.getUuid(),
                docRef.getName(),
                null,
                null,
                null,
                null,
                null);
        final ObjectMapper mapper = JsonUtil.getNoIndentMapper();
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

    private static class GenericDoc extends AbstractDoc {

        public GenericDoc(@JsonProperty("uuid") final String uuid,
                          @JsonProperty("name") final String name,
                          @JsonProperty("version") final String version,
                          @JsonProperty("createTimeMs") final Long createTimeMs,
                          @JsonProperty("updateTimeMs") final Long updateTimeMs,
                          @JsonProperty("createUser") final String createUser,
                          @JsonProperty("updateUser") final String updateUser) {
            super("GenericDoc", uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        }
    }
}
