package stroom.docstore.impl.fs;

import org.junit.jupiter.api.Test;
import stroom.docref.DocRef;
import stroom.docstore.api.Persistence;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestFSPersistence {
    private static final Charset CHARSET = Charset.forName("UTF-8");

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

        // Create
        Map<String, byte[]> data = new HashMap<>();
        data.put("meta", uuid1.getBytes(CHARSET));
        persistence.write(docRef, false, data);

        // Exists
        assertThat(persistence.exists(docRef)).isTrue();

        // Read
        data = persistence.read(docRef);
        assertThat(new String(data.get("meta"), CHARSET)).isEqualTo(uuid1);

        // Update
        data = new HashMap<>();
        data.put("meta", uuid2.getBytes(CHARSET));
        persistence.write(docRef, true, data);

        // Read
        data = persistence.read(docRef);
        assertThat(new String(data.get("meta"), CHARSET)).isEqualTo(uuid2);

        // List
        final List<DocRef> refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.get(0)).isEqualTo(docRef);

        // Delete
        persistence.delete(docRef);
    }
}
