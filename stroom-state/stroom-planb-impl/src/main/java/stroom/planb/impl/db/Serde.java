package stroom.planb.impl.db;

import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;

import org.lmdbjava.CursorIterable.KeyVal;

import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Serde<K, V> {

    <R> R createKeyByteBuffer(K key, Function<ByteBuffer, R> function);

    <R> R createValueByteBuffer(K key, V value, Function<ByteBuffer, R> function);

    <R> R createPrefixPredicate(K key, Function<Predicate<KeyVal<ByteBuffer>>, R> function);

    <R> R createPrefixPredicate(ByteBuffer keyByteBuffer,
                                ByteBuffer valueByteBuffer,
                                Function<Predicate<KeyVal<ByteBuffer>>, R> function);

    boolean hasPrefix();

    Function<KeyVal<ByteBuffer>, Val>[] getValExtractors(FieldIndex fieldIndex);

    K getKey(KeyVal<ByteBuffer> keyVal);

    V getVal(KeyVal<ByteBuffer> keyVal);
}
