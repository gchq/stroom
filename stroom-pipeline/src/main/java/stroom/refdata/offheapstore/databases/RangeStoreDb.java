/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.refdata.offheapstore.databases;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.offheapstore.ByteArrayUtils;
import stroom.refdata.offheapstore.RangeStoreKey;
import stroom.refdata.offheapstore.UID;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.RangeStoreKeySerde;
import stroom.refdata.offheapstore.serdes.UIDSerde;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Optional;

public class RangeStoreDb extends AbstractLmdbDb<RangeStoreKey, ValueStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RangeStoreDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RangeStoreDb.class);

    private static final String DB_NAME = "RangeStore";

    @Inject
    public RangeStoreDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                        final RangeStoreKeySerde keySerde,
                        final ValueStoreKeySerde valueSerde) {

        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    /**
     * Looks up the supplied mapDefinitionUid and key in the store and attempts to find a range that includes the
     * passed key. It will scan backwards from key until it finds the first matching range. If no matching range
     * is found an empty Optional is returned.
     */
    public Optional<ValueStoreKey> get(final Txn<ByteBuffer> txn, final UID mapDefinitionUid, final long key) {

        LOGGER.trace("get called for {}, key {}", mapDefinitionUid, key);

        final KeyRange<ByteBuffer> keyRange = buildKeyRange(mapDefinitionUid, key);

        // these lines are useful for debugging with SMALL data volumes
//        logDatabaseContents(txn);
//        logRawDatabaseContents(txn);
//        LmdbUtils.logRawContentsInRange(lmdbEnvironment, lmdbDbi, txn, keyRange);
//        LmdbUtils.logContentsInRange(
//                lmdbEnvironment,
//                lmdbDbi,
//                txn,
//                keyRange,
//                buf -> keySerde.deserialize(buf).toString(),
//                buf -> valueSerde.deserialize(buf).toString());

        try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(txn, keyRange)) {
            int cnt = 0;
            // loop backwards over all rows with the same mapDefinitionUid, starting at key
            for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                cnt++;
                final ByteBuffer keyBuffer = keyVal.key();

                if (LOGGER.isTraceEnabled()) {
                    RangeStoreKey rangeStoreKey = keySerde.deserialize(keyBuffer);
                    LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("rangeStoreKey {}, keyBuffer {}",
                            rangeStoreKey,
                            ByteArrayUtils.byteBufferInfo(keyBuffer)));
                }

                if (RangeStoreKeySerde.isKeyInRange(keyBuffer, key)) {
                    final UID uidOfFoundKey = UIDSerde.extractUid(keyBuffer);
                    // double check to be sure we have the right mapDefinitionUid
                    if (!uidOfFoundKey.equals(mapDefinitionUid)) {
                        throw new RuntimeException(LambdaLogger.buildMessage(
                                "Found a key with a different mapDefinitionUid, found: {}, expected {}",
                                uidOfFoundKey, mapDefinitionUid));
                    }
                    LOGGER.trace("key {} is in the range, found the required value after {} iterations", key, cnt);
                    return Optional.of(valueSerde.deserialize(keyVal.val()));
                }
            }
        }
        LOGGER.trace("Value not found for {}, key {}", mapDefinitionUid, key);
        return Optional.empty();
    }

    /**
     * Returns true if the store contains at least one row with a key containing the passed mapDefinitionUid
     */
    public boolean containsMapDefinition(final Txn<ByteBuffer> txn, final UID mapDefinitionUid) {

        LOGGER.trace("containsMapDefinition called for {}, key {}", mapDefinitionUid);

        final Range<Long> startRange = new Range<>(0L, 0L);
        final RangeStoreKey startRangeStoreKey = new RangeStoreKey(mapDefinitionUid, startRange);
        final ByteBuffer startKeyBuf = keySerde.serialize(startRangeStoreKey);

        final KeyRange<ByteBuffer> keyRange = KeyRange.atLeast(startKeyBuf);

        try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(txn, keyRange)) {
            return cursorIterator.hasNext();
        }
    }

    private KeyRange<ByteBuffer> buildKeyRange(final UID mapDefinitionUid, final long key) {
        // We want to scan backwards over all keys with the passed mapDefinitionUid,
        // starting with a range from == key. E.g. with the following data (ignoring mapDefUid)
        // in DB order.

        // 100-200
        // 200-300
        // 300-400
        // 400-500

        // key  | DB keys returned
        // -----|-----------------
        // 50   | [no rows]
        // 150  | 100-200
        // 300  | 300-400, 200-300, 100-200
        // 1000 | 400-500, 300-400, 200-300, 100-200

        // Set the 'to' bit of the start key to Long.MAX_VALUE to ensure we get all possible values
        // with that 'from' part.
        final Range<Long> startRange = new Range<>(key, Long.MAX_VALUE);
        final RangeStoreKey startRangeStoreKey = new RangeStoreKey(mapDefinitionUid, startRange);
        final ByteBuffer startKeyBuf = keySerde.serialize(startRangeStoreKey);

        // Zero is the lowest value for 'from' and 'to' so this represents the smallest key for
        // a given mapDefinitionUid.
        final Range<Long> endRange = new Range<>(0L, 0L);
        final RangeStoreKey endRangeStoreKey = new RangeStoreKey(mapDefinitionUid, endRange);
        final ByteBuffer endKeyBuf = keySerde.serialize(endRangeStoreKey);

        LOGGER.debug("Using range [{}] to [{}]", endRangeStoreKey, startRangeStoreKey);
        // we want to scan backward from (and including, if found) our start key
        return KeyRange.closedBackward(startKeyBuf, endKeyBuf);
    }

    public interface Factory {
        RangeStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
