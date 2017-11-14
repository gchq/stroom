package stroom.lmdb;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestLmdbKeyValueStoreJava {

    private static final String DB_NAME = "myLmdb";

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void testStringString_db() throws IOException {

        Path path = Paths.get("/tmp/lmdb");
        Files.createDirectories(path);

        final Env<ByteBuffer> env = Env.<ByteBuffer>create()
                .setMapSize(10_485_760)
                .setMaxDbs(1)
                .open(path.toFile());

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, DbiFlags.MDB_CREATE);

        final ByteBuffer key = ByteBuffer.allocateDirect(env.getMaxKeySize());
        final ByteBuffer value = ByteBuffer.allocateDirect(700);
        key.put("greeting".getBytes(StandardCharsets.UTF_8)).flip();
        value.put("Hello world".getBytes(StandardCharsets.UTF_8)).flip();

        final int valSize = value.remaining();

//        db.put(key, value);

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            final ByteBuffer found = db.get(txn, key);
            Assert.assertNotNull(found);

            // The fetchedVal is read-only and points to LMDB memory
            final ByteBuffer fetchedVal = txn.val();
            Assert.assertThat(fetchedVal.remaining(), CoreMatchers.is(valSize));

            // Let's double-check the fetched value is correct
            Assert.assertThat(StandardCharsets.UTF_8.decode(fetchedVal).toString(),
                    CoreMatchers.is("Hello world"));
        }

        // We can also delete. The simplest way is to let Dbi allocate a new Txn...
//        db.delete(key);

        // Now if we try to fetch the deleted row, it won't be present
        try (Txn<ByteBuffer> txn = env.txnRead()) {
//            Assert.assertNull(db.get(txn, key));
        }
    }

    @Test
    public void perfTest() throws IOException {

        List<Map.Entry<String, String>> entries = IntStream.rangeClosed(0, 1000)
                .mapToObj(i -> Maps.immutableEntry(
                        "key" + i + UUID.randomUUID().toString(),
                        UUID.randomUUID().toString()))
                .collect(Collectors.toList());

        List<Map.Entry<String, String>> randomEntries = new ArrayList<>(entries);
        Collections.shuffle(randomEntries);

        Path dir = tmpDir.newFolder().toPath();
        KeyValueStore lmdbKeyValueStore = new LmdbKeyValueStore("MyDB", dir);

        KeyValueStore inMemoryKeyValueStore = new InMemoryKeyValueStore();

        List<KeyValueStore> stores = Arrays.asList(lmdbKeyValueStore, inMemoryKeyValueStore);

        stores.forEach(store -> {
            Instant start = Instant.now();

            entries.forEach(entry ->
                    store.put(entry.getKey(), entry.getValue()));

            System.out.println(String.format("Put duration %s", Duration.between(start, Instant.now()).toString()));
        });

        stores.forEach(store -> {
            Instant start = Instant.now();

            randomEntries.forEach(entry -> {
                Optional<String> val = store.get(entry.getKey());
                Preconditions.checkNotNull(val);
            });

            System.out.println(String.format("Get duration %s", Duration.between(start, Instant.now()).toString()));
        });
    }
}
