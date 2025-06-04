package stroom.planb.impl.serde.valtime;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.serde.hash.HashFactory;
import stroom.planb.impl.serde.hash.HashFactoryFactory;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.StateValueType;

public class ValTimeSerdeFactory {
    private static final String VALUE_LOOKUP_DB_NAME = "value";

    public static ValTimeSerde createValueSerde(final StateValueType stateValueType,
                                                 final HashLength hashLength,
                                                 final PlanBEnv env,
                                                 final ByteBuffers byteBuffers,
                                                 final HashClashCommitRunnable hashClashCommitRunnable) {
        final InsertTimeSerde timeSerde = new InsertTimeSerde();
        return switch (stateValueType) {
            case BOOLEAN -> new BooleanValTimeSerde(timeSerde);
            case BYTE -> new ByteValTimeSerde(timeSerde);
            case SHORT -> new ShortValTimeSerde(timeSerde);
            case INT -> new IntegerValTimeSerde(timeSerde);
            case LONG -> new LongValTimeSerde(timeSerde);
            case FLOAT -> new FloatValTimeSerde(timeSerde);
            case DOUBLE -> new DoubleValTimeSerde(timeSerde);
            case STRING -> new StringValTimeSerde(byteBuffers, timeSerde);
            case UID_LOOKUP -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        VALUE_LOOKUP_DB_NAME);
                yield new UidLookupValTimeSerde(uidLookupDb, byteBuffers, timeSerde);
            }
            case HASH_LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        VALUE_LOOKUP_DB_NAME);
                yield new HashLookupValTimeSerde(hashLookupDb, byteBuffers, timeSerde);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        VALUE_LOOKUP_DB_NAME);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        VALUE_LOOKUP_DB_NAME);
                yield new VariableValTimeSerde(uidLookupDb, hashLookupDb, byteBuffers, timeSerde);
            }
        };
    }
}
