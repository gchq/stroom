package stroom.planb.impl.db;

import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;

import java.nio.file.Path;

public interface Db<K, V> extends AutoCloseable {

    void insert(LmdbWriter writer,
                KV<K, V> kv);

    V get(K key);

    void search(ExpressionCriteria criteria,
                FieldIndex fieldIndex,
                DateTimeSettings dateTimeSettings,
                ExpressionPredicateFactory expressionPredicateFactory,
                ValuesConsumer consumer);

    void merge(Path source);

    void condense(long condenseBeforeMs,
                  long deleteBeforeMs);

    LmdbWriter createWriter();

    void lock(Runnable runnable);

    void close();

    long count();

    boolean isReadOnly();

    String getInfo();
}
