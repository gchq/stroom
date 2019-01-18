package stroom.docstore.db;


import org.junit.jupiter.api.Test;
import stroom.docref.DocRef;
import stroom.docstore.Persistence;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestDBPersistence extends AbstractCoreIntegrationTest {
    private static final Charset CHARSET = Charset.forName("UTF-8");

    @Inject
    private Persistence persistence;

    @Test
    void test() throws IOException {
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
