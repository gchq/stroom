package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.hash.HashClashCount;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.hash.HashFactoryFactory;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.NullSafe;

import java.nio.file.Path;

class HashedKeySchema extends AbstractSchema<String, StateValue> {

    private final HashedKeySupport hashedKeySupport;

    public HashedKeySchema(final PlanBEnv env,
                           final ByteBuffers byteBuffers,
                           final StateSettings settings,
                           final HashClashCount hashClashCount,
                           final StateValueSerde stateValueSerde) {
        super(env, byteBuffers);
        final HashFactory hashFactory = HashFactoryFactory.create(NullSafe.get(
                settings,
                StateSettings::getStateKeySchema,
                StateKeySchema::getHashLength));
        final boolean overwrite = settings.overwrite();
        hashedKeySupport = new HashedKeySupport(
                env,
                dbi,
                byteBuffers,
                hashFactory,
                overwrite,
                hashClashCount,
                stateValueSerde);
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<String, StateValue> kv) {
        hashedKeySupport.insert(writer, kv.key(), kv.val());
    }

    @Override
    public StateValue get(final String key) {
        return hashedKeySupport.get(key);
    }

    @Override
    public void merge(final Path source) {
        hashedKeySupport.merge(source);
    }


    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        hashedKeySupport.search(criteria, fieldIndex, dateTimeSettings, expressionPredicateFactory, consumer);
    }
}
