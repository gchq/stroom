package stroom.query.common.v2;

import stroom.query.language.functions.ref.DataReader;
import stroom.query.language.functions.ref.DataWriter;
import stroom.query.language.functions.ref.ErrorConsumer;

import java.util.Set;

public interface KeyFactory extends UniqueIdProvider {

    /**
     * Write a key to an output.
     *
     * @param key The key to serialise.
     */
    void write(Key key, DataWriter writer);

    /**
     * Read a key from an input.
     *
     * @param input The input to read the key from.
     * @return The key read from the input.
     */
    Key read(DataReader reader);

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
}
