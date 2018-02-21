package stroom.docstore.db;

import org.junit.Assert;
import org.junit.Test;
import stroom.docstore.Persistence;
import stroom.query.api.v2.DocRef;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.StreamUtil;

import javax.annotation.Resource;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;


public class TestDBPersistence extends AbstractCoreIntegrationTest {
    @Resource
    private Persistence persistence;

    @Test
    public void test() throws Exception {
        final String data = UUID.randomUUID().toString();
        final String data2 = UUID.randomUUID().toString();
        final DocRef docRef = new DocRef("test-type", "test-uuid", "test-name");

        // Ensure the doc doesn't exist.
        if (persistence.exists(docRef)) {
            persistence.delete(docRef);
        }

        // Create
        try (final OutputStream outputStream = persistence.getOutputStream(docRef, false)) {
            outputStream.write(data.getBytes(Charset.forName("UTF-8")));
        }

        // Exists
        Assert.assertTrue(persistence.exists(docRef));

        // Read
        try (final InputStream inputStream = persistence.getInputStream(docRef)) {
            final String stored = StreamUtil.streamToString(inputStream);
            Assert.assertEquals(data, stored);
        }

        // Update
        try (final OutputStream outputStream = persistence.getOutputStream(docRef, true)) {
            outputStream.write(data2.getBytes(Charset.forName("UTF-8")));
        }

        // Read
        try (final InputStream inputStream = persistence.getInputStream(docRef)) {
            final String stored = StreamUtil.streamToString(inputStream);
            Assert.assertEquals(data2, stored);
        }

        // List
        final List<DocRef> refs = persistence.list(docRef.getType());
        Assert.assertEquals(1, refs.size());
        Assert.assertEquals(docRef, refs.get(0));

        // Delete
        persistence.delete(docRef);
    }
}
