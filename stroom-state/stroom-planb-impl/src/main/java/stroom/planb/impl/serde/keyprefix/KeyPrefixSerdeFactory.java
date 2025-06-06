package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.serde.hash.HashFactory;
import stroom.planb.impl.serde.hash.HashFactoryFactory;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.KeyType;

public class KeyPrefixSerdeFactory {
    private static final String KEY_LOOKUP_DB_NAME = "key";

    public static KeyPrefixSerde createKeySerde(final KeyType keyType,
                                                final HashLength hashLength,
                                                final PlanBEnv env,
                                                final ByteBuffers byteBuffers,
                                                final HashClashCommitRunnable hashClashCommitRunnable) {
        return switch (keyType) {
            case BOOLEAN -> new BooleanKeySerde(byteBuffers);
            case BYTE -> new ByteKeySerde(byteBuffers);
            case SHORT -> new ShortKeySerde(byteBuffers);
            case INT -> new IntegerKeySerde(byteBuffers);
            case LONG -> new LongKeySerde(byteBuffers);
            case FLOAT -> new FloatKeySerde(byteBuffers);
            case DOUBLE -> new DoubleKeySerde(byteBuffers);
            case STRING -> new LimitedStringKeySerde(byteBuffers);
            case UID_LOOKUP -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new UidLookupKeySerde(uidLookupDb, byteBuffers);
            }
            case HASH_LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new HashLookupKeySerde(hashLookupDb, byteBuffers);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new VariableKeySerde(uidLookupDb, hashLookupDb, byteBuffers);
            }
            case TAGS -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new TagsKeySerde(uidLookupDb, byteBuffers);
            }
        };
    }
}
