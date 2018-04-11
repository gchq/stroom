package stroom.docstore.fs;

import org.junit.Assert;
import org.junit.Test;
import stroom.docstore.Persistence;
import stroom.query.api.v2.DocRef;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;


public class TestFSPersistence {
    @Test
    public void test() throws IOException {
        final Persistence persistence = new FSPersistence(Files.createTempDirectory("docstore").resolve("conf"));

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
            final String stored = streamToString(inputStream);
            Assert.assertEquals(data, stored);
        }

        // Update
        try (final OutputStream outputStream = persistence.getOutputStream(docRef, true)) {
            outputStream.write(data2.getBytes(Charset.forName("UTF-8")));
        }

        // Read
        try (final InputStream inputStream = persistence.getInputStream(docRef)) {
            final String stored = streamToString(inputStream);
            Assert.assertEquals(data2, stored);
        }

        // List
        final List<DocRef> refs = persistence.list(docRef.getType());
        Assert.assertEquals(1, refs.size());
        Assert.assertEquals(docRef, refs.get(0));

        // Delete
        persistence.delete(docRef);
    }

    private String streamToString(final InputStream inputStream) throws IOException {
        final byte[] buffer = new byte[1024];
        int len;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer, 0, buffer.length)) != -1) {
            byteArrayOutputStream.write(buffer, 0, len);
        }
        return new String(byteArrayOutputStream.toByteArray(), "UTF-8");
    }
}
