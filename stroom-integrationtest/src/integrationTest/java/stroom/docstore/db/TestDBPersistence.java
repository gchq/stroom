package stroom.docstore.db;

import org.junit.Assert;
import org.junit.Test;
import stroom.docstore.Persistence;
import stroom.docref.DocRef;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class TestDBPersistence extends AbstractCoreIntegrationTest {
    private static final Charset CHARSET = Charset.forName("UTF-8");

    @Inject
    private Persistence persistence;

    @Test
    public void test() throws IOException {
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
        Assert.assertTrue(persistence.exists(docRef));

        // Read
        data = persistence.read(docRef);
        Assert.assertEquals(uuid1, new String(data.get("meta"), CHARSET));

        // Update
        data = new HashMap<>();
        data.put("meta", uuid2.getBytes(CHARSET));
        persistence.write(docRef, true, data);

        // Read
        data = persistence.read(docRef);
        Assert.assertEquals(uuid2, new String(data.get("meta"), CHARSET));

        // List
        final List<DocRef> refs = persistence.list(docRef.getType());
        Assert.assertEquals(1, refs.size());
        Assert.assertEquals(docRef, refs.get(0));

        // Delete
        persistence.delete(docRef);
    }
}
