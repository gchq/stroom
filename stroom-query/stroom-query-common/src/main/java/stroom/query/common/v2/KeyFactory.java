package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.ErrorConsumer;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.nio.ByteBuffer;
import java.util.Set;

public interface KeyFactory {

    /**
     * Write a key to an output.
     *
     * @param key The key to serialise.
     */
    void write(Key key, Output output);

    /**
     * Read a key from an input.
     *
     * @param input The input to read the key from.
     * @return The key read from the input.
     */
    Key read(Input input);

    /**
     * Read a key from a byteBuffer.
     *
     * @param byteBuffer The byteBuffer to read the key from.
     * @return The key read from the byteBuffer.
     */
    Key read(ByteBuffer byteBuffer);

    /**
     * Decode a set of string encoded key bytes into a set of keys.
     *
     * @param openGroups The set of encoded key bytes to turn into a set of keys.
     * @return A decoded set of keys.
     */
    Set<Key> decodeSet(Set<String> openGroups);

    /**
     * Encode a key into an encoded string from the key bytes.
     *
     * @param key           The key to encode.
     * @param errorConsumer The error consumer to consume all errors that occur during the conversion.
     * @return The bytes of a key encoded into a string.
     */
    String encode(Key key, ErrorConsumer errorConsumer);

    /**
     * Create a unique id.
     *
     * @return A unique id.
     */
    long getUniqueId();
}
