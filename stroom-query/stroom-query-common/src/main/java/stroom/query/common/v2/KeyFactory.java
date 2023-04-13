package stroom.query.common.v2;

import java.util.Set;

public interface KeyFactory {

    /**
     * Turn the key into a byte array.
     *
     * @param key           The key to turn into a byte array.
     * @param errorConsumer The error consumer to consume all errors that occur during the conversion.
     * @return The key as a byte array.
     */
    byte[] keyToBytes(Key key, ErrorConsumer errorConsumer);

    /**
     * Turn a byte array into a key.
     *
     * @param bytes The bytes to create the key from.
     * @return The key created from the supplied byte array.
     */
    Key keyFromBytes(byte[] bytes);

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
