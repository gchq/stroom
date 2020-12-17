package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.query.api.v2.TableSettings;
import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.hadoop.hbase.util.Bytes;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class LmdbDataStore implements DataStore {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDataStore.class);

    private static final String DEFAULT_STORE_SUB_DIR_NAME = "searchResults";

    // These are dups of org.lmdbjava.Library.LMDB_* but that class is pkg private for some reason.
    private static final String LMDB_EXTRACT_DIR_PROP = "lmdbjava.extract.dir";
    private static final String LMDB_NATIVE_LIB_PROP = "lmdbjava.native.lib";

    private final TempDirProvider tempDirProvider;
    private final LmdbConfig lmdbConfig;
    private final PathCreator pathCreator;
    private final Path dbDir;
    private final ByteSize maxSize;
    private final int maxReaders;
    private final int maxPutsBeforeCommit;


    private final KeyValueStoreDb keyValueStoreDb;


    private static RawKey ROOT_KEY;

    private final AtomicLong ungroupedItemSequenceNumber = new AtomicLong();

    private final CompiledField[] compiledFields;
    private final CompiledDepths compiledDepths;

    @Inject
    public LmdbDataStore(final TempDirProvider tempDirProvider,
                         final LmdbConfig lmdbConfig,
                         final PathCreator pathCreator,
                         final ByteBufferPool byteBufferPool,
                         final TableSettings tableSettings,
                         final FieldIndex fieldIndex,
                         final Map<String, String> paramMap,
                         final Sizes maxResults,
                         final Sizes storeSize) {
        this.tempDirProvider = tempDirProvider;
        this.lmdbConfig = lmdbConfig;
        this.pathCreator = pathCreator;
        this.dbDir = getStoreDir();
        this.maxSize = lmdbConfig.getMaxStoreSize();
        this.maxReaders = lmdbConfig.getMaxReaders();
        this.maxPutsBeforeCommit = lmdbConfig.getMaxPutsBeforeCommit();

        final Env<ByteBuffer> lmdbEnvironment = createEnvironment(lmdbConfig);

        compiledFields = CompiledFields.create(tableSettings.getFields(), fieldIndex, paramMap);
        final CompiledDepths compiledDepths = new CompiledDepths(compiledFields, tableSettings.showDetail());
        final LmdbCompiledSorter[] compiledSorters = LmdbCompiledSorter.create(compiledDepths.getMaxDepth(), compiledFields);
        this.compiledDepths = compiledDepths;

        final ItemSerialiser itemSerialiser = new ItemSerialiser(compiledFields);
        if (ROOT_KEY == null) {
            ROOT_KEY = itemSerialiser.toRawKey(new Key(Collections.emptyList()));
        }

        final KeySerde keySerde = new KeySerde(itemSerialiser);
        final ValueSerde valueSerde = new ValueSerde(itemSerialiser);
        keyValueStoreDb = new KeyValueStoreDb(
                lmdbEnvironment,
                byteBufferPool,
                keySerde,
                valueSerde,
                itemSerialiser,
                compiledFields,
                compiledSorters,
                compiledDepths,
                maxResults,
                storeSize);
    }

    @Override
    public void clear() {
        keyValueStoreDb.clear();
    }

    @Override
    public boolean readPayload(final Input input) {
        return keyValueStoreDb.readPayload(input);
    }

    @Override
    public void writePayload(final Output output) {
        keyValueStoreDb.writePayload(output);
    }

    @Override
    public void add(final Val[] values) {
        final int[] groupSizeByDepth = compiledDepths.getGroupSizeByDepth();
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        final List<KeyPart> groupKeys = new ArrayList<>(groupIndicesByDepth.length);

//        byte[] parentKey = ROOT_KEY.getBytes();
        for (int depth = 0; depth < groupIndicesByDepth.length; depth++) {
            final Generator[] generators = new Generator[compiledFields.length];

            final int groupSize = groupSizeByDepth[depth];
            final boolean[] groupIndices = groupIndicesByDepth[depth];
            final boolean[] valueIndices = valueIndicesByDepth[depth];

            Val[] groupValues = ValSerialiser.EMPTY_VALUES;
            if (groupSize > 0) {
                groupValues = new Val[groupSize];
            }

            int groupIndex = 0;
            for (int fieldIndex = 0; fieldIndex < compiledFields.length; fieldIndex++) {
                final CompiledField compiledField = compiledFields[fieldIndex];

                final Expression expression = compiledField.getExpression();
                if (expression != null) {
                    if (groupIndices[fieldIndex] || valueIndices[fieldIndex]) {
                        final Generator generator = expression.createGenerator();
                        generator.set(values);

                        if (groupIndices[fieldIndex]) {
                            groupValues[groupIndex++] = generator.eval();
                        }

                        if (valueIndices[fieldIndex]) {
                            generators[fieldIndex] = generator;
                        }
                    }
                }
            }

            // Trim group values.
            if (groupIndex < groupSize) {
                groupValues = Arrays.copyOf(groupValues, groupIndex);
            }

            KeyPart keyPart;
            if (depth <= compiledDepths.getMaxGroupDepth()) {
                // This is a grouped item.
                keyPart = new GroupKeyPart(groupValues);

            } else {
                // This item will not be grouped.
                keyPart = new UngroupedKeyPart(ungroupedItemSequenceNumber.incrementAndGet());
            }

            groupKeys.add(keyPart);
            keyValueStoreDb.put(new Key(new ArrayList<>(groupKeys)), generators);
        }
    }

    @Override
    public Items get() {
        return get(ROOT_KEY);
    }

    @Override
    public long getSize() {
        return keyValueStoreDb.getSize();
    }

    @Override
    public long getTotalSize() {
        return keyValueStoreDb.getTotalSize();
    }

    @Override
    public Items get(final RawKey rawKey) {
        return keyValueStoreDb.get(rawKey);
    }


    private Env<ByteBuffer> createEnvironment(final LmdbConfig lmdbConfig) {
        LOGGER.info(
                "Creating RefDataOffHeapStore environment with [maxSize: {}, dbDir {}, maxReaders {}, " +
                        "maxPutsBeforeCommit {}, isReadAheadEnabled {}]",
                maxSize,
                dbDir.toAbsolutePath().toString() + File.separatorChar,
                maxReaders,
                maxPutsBeforeCommit,
                lmdbConfig.isReadAheadEnabled());

        // By default LMDB opens with readonly mmaps so you cannot mutate the bytebuffers inside a txn.
        // Instead you need to create a new bytebuffer for the value and put that. If you want faster writes
        // then you can use EnvFlags.MDB_WRITEMAP in the open() call to allow mutation inside a txn but that
        // comes with greater risk of corruption.

        // NOTE on setMapSize() from LMDB author found on https://groups.google.com/forum/#!topic/caffe-users/0RKsTTYRGpQ
        // On Windows the OS sets the filesize equal to the mapsize. (MacOS requires that too, and allocates
        // all of the physical space up front, it doesn't support sparse files.) The mapsize should not be
        // hardcoded into software, it needs to be reconfigurable. On Windows and MacOS you really shouldn't
        // set it larger than the amount of free space on the filesystem.

        final EnvFlags[] envFlags;
        if (lmdbConfig.isReadAheadEnabled()) {
            envFlags = new EnvFlags[0];
        } else {
            envFlags = new EnvFlags[]{EnvFlags.MDB_NORDAHEAD};
        }

        final String lmdbSystemLibraryPath = lmdbConfig.getLmdbSystemLibraryPath();

        if (lmdbSystemLibraryPath != null) {
            // javax.validation should ensure the path is valid if set
            System.setProperty(LMDB_NATIVE_LIB_PROP, lmdbSystemLibraryPath);
            LOGGER.info("Using provided LMDB system library file " + lmdbSystemLibraryPath);
        } else {
            // Set the location to extract the bundled LMDB binary to
            System.setProperty(LMDB_EXTRACT_DIR_PROP, dbDir.toAbsolutePath().toString());
            LOGGER.info("Extracting bundled LMDB binary to " + dbDir);
        }

        final Env<ByteBuffer> env = Env.create()
                .setMaxReaders(maxReaders)
                .setMapSize(maxSize.getBytes())
                .setMaxDbs(7) //should equal the number of DBs we create which is fixed at compile time
                .open(dbDir.toFile(), envFlags);

        LOGGER.info("Existing databases: [{}]",
                env.getDbiNames()
                        .stream()
                        .map(Bytes::toString)
                        .collect(Collectors.joining(",")));
        return env;
    }

    private Path getStoreDir() {
        String storeDirStr = pathCreator.replaceSystemProperties(lmdbConfig.getLocalDir());
        Path storeDir;
        if (storeDirStr == null) {
            LOGGER.info("Off heap store dir is not set, falling back to {}", tempDirProvider.get());
            storeDir = tempDirProvider.get();
            Objects.requireNonNull(storeDir, "Temp dir is not set");
            storeDir = storeDir.resolve(DEFAULT_STORE_SUB_DIR_NAME);
        } else {
            storeDirStr = pathCreator.replaceSystemProperties(storeDirStr);
            storeDir = Paths.get(storeDirStr);
        }

        try {
            LOGGER.debug("Ensuring directory {}", storeDir);
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error ensuring store directory {} exists", storeDirStr), e);
        }

        return storeDir;
    }

    @Override
    public void complete() throws InterruptedException {
        keyValueStoreDb.complete();
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        keyValueStoreDb.awaitCompletion();
    }
}
