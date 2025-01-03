package stroom.planb.impl.dao;

import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;

import org.lmdbjava.CursorIterable.KeyVal;

import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Serde<K, V> {

    <R> R createKeyByteBuffer(K key, Function<ByteBuffer, R> function);

    <R> R createPrefixPredicate(K key, Function<Predicate<KeyVal<ByteBuffer>>, R> function);

    <R> R createValueByteBuffer(K key, V value, Function<ByteBuffer, R> function);

    Function<KeyVal<ByteBuffer>, Val>[] getValExtractors(FieldIndex fieldIndex);

    V get(KeyVal<ByteBuffer> keyVal);
}
