package stroom.docstore.server.fs;

import org.junit.Assert;
import org.junit.Test;
import stroom.docstore.server.Persistence;
import stroom.query.api.v2.DocRef;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;


public class TestFSPersistence {
    @Test
    public void test() throws Exception {
        final Persistence persistence = new FSPersistence(FileUtil.getTempDir().resolve("conf"));

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
        final Set<DocRef> refs = persistence.list(docRef.getType());
        Assert.assertEquals(1, refs.size());
        Assert.assertEquals(docRef, refs.iterator().next());

        // Delete
        persistence.delete(docRef);
    }
}
