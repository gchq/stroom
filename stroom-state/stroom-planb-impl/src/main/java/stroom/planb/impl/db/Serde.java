package stroom.planb.impl.db;

import stroom.lmdb2.BBKV;
import stroom.lmdb2.KV;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;

import org.lmdbjava.CursorIterable.KeyVal;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Serde<K, V> {

    <R> R createKeyByteBuffer(K key, Function<ByteBuffer, R> function);

    <R> R createValueByteBuffer(K key, V value, Function<ByteBuffer, R> function);

    <R> R createPrefixPredicate(K key, Function<Predicate<BBKV>, R> function);

    void createPrefixPredicate(BBKV kv, Consumer<Predicate<BBKV>> consumer);

    boolean hasPrefix();

    Function<KeyVal<ByteBuffer>, Val>[] getValExtractors(FieldIndex fieldIndex);

    K getKey(BBKV kv);

    V getVal(BBKV kv);

    int getKeyLength();
}
