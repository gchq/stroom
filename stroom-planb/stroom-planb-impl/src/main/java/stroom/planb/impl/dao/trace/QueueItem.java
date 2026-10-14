/*
 * Copyright 2026 Crown Copyright
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
 */

package stroom.planb.impl.dao.trace;

import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.dao.PlanBEnv;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A batch of finished traces handed from a trace store to whatever consumes them, as one
 * self-contained LMDB environment.
 *
 * <p>An item holds the spans of its traces, their stored roots, and the four lookup tables those
 * spans reference. A span value does not carry its own strings — long names and attribute text are
 * stored once and referenced by number — so the tables have to travel with the spans or the spans
 * decode to nothing. Roots need no such help: a root value writes its name directly.
 *
 * <p>This class holds what the writer and the reader must agree on. Both open the same environment,
 * and LMDB and {@link TraceDb} both take some of these as fixed per environment rather than per
 * open, so a disagreement is a failure to open rather than a wrong answer.
 */
public final class QueueItem {

    /**
     * The item layout: which tables are present and what the info entries mean. Raised whenever a
     * reader written against an older layout could misread a newer item. Note this is separate from
     * the schema {@link TraceDb} itself validates on open, which covers the span and lookup encoding.
     */
    public static final int FORMAT_VERSION = 1;

    /**
     * Items carry no secondary sort indexes. Nothing sorts or filters an item — a consumer walks its
     * roots and pulls each trace's spans by key prefix — so maintaining them would be write
     * amplification. It is a constant rather than a parameter because {@link TraceDb} requires the
     * same answer on every open of an environment.
     */
    static final boolean HAS_SECONDARY_INDEXES = false;

    private static final String INFO_DBI_NAME = "queue-item-info";
    private static final byte INFO_KEY_FORMAT_VERSION = 0;
    private static final byte INFO_KEY_ORDER_KEY = 1;
    /**
     * The uuid of the trace store these traces came from. Held so that what is learnt from them can
     * say where it came from, which configuration cannot: a store can be pointed at a different
     * pathways document, and what it taught the old one stays behind.
     */
    private static final byte INFO_KEY_SOURCE = 2;

    /** Zero padded so that ordering by name and ordering by key are the same thing. */
    private static final String NAME_FORMAT = "%013d_%s";
    private static final Pattern NAME_PATTERN = Pattern.compile("^(\\d{13})_[0-9a-f-]{36}$");

    /** Orders items by the key their producer stamped them with, oldest first. */
    public static final Comparator<Path> BY_ORDER_KEY =
            Comparator.comparing(path -> path.getFileName().toString());

    private QueueItem() {
    }

    /**
     * A name for a new item. The order key leads it so that a consumer can take items in order, and
     * report how old the oldest one is, by listing a directory rather than opening every environment
     * in it. The random suffix is what keeps two trace shards writing in the same millisecond apart.
     *
     * <p>The name is a convenience. {@link #readOrderKey} is the stored answer.
     */
    public static String newName(final long orderKey) {
        return NAME_FORMAT.formatted(orderKey, UUID.randomUUID());
    }

    /**
     * The name a part-written item is built under. The same suffix every other part-written directory
     * on the shared store uses, so the one sweep that tidies abandoned ones covers these too.
     */
    public static String tmpName(final String name) {
        return name + PlanBConstants.TMP_DIR_SUFFIX;
    }

    /**
     * Whether the path is a finished item. Part-written items carry a temporary name and anything else
     * in the directory is not ours, so both are skipped rather than failed on.
     */
    public static boolean isItem(final Path path) {
        return Files.isDirectory(path) && NAME_PATTERN.matcher(path.getFileName().toString()).matches();
    }

    /**
     * The order key encoded in an item's name. Prefer this over opening the item when all that is
     * wanted is the age of the oldest one.
     *
     * @throws IllegalArgumentException where the path is not an item name.
     */
    public static long orderKeyOf(final Path path) {
        final String name = path.getFileName().toString();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a queue item name: " + name);
        }
        return Long.parseLong(name.substring(0, 13));
    }

    /**
     * The document an item's environment is opened with. Items are not documents and have no
     * settings of their own, so both sides build this the same way and get the same map size — which
     * has to match, because LMDB will not open an environment whose data outgrew the map it is given.
     */
    static PlanBDoc doc() {
        return PlanBDoc.builder()
                .uuid("queue-item")
                .name("queue-item")
                .stateType(StateType.TRACE)
                // No max store size: both sides then take the LMDB default, which is sparse, so a
                // generous map costs nothing until it is used.
                .settings(new TraceSettings.Builder().build())
                .build();
    }

    static void writeInfo(final PlanBEnv env, final long orderKey, final String source) {
        final Dbi<ByteBuffer> dbi = infoDbi(env);
        env.write(writer -> {
            putLong(dbi, writer, INFO_KEY_FORMAT_VERSION, FORMAT_VERSION);
            putLong(dbi, writer, INFO_KEY_ORDER_KEY, orderKey);
            if (source != null) {
                putString(dbi, writer, INFO_KEY_SOURCE, source);
            }
            writer.commit();
        });
    }

    static int readFormatVersion(final PlanBEnv env) {
        return (int) readLong(env, INFO_KEY_FORMAT_VERSION);
    }

    static long readOrderKey(final PlanBEnv env) {
        return readLong(env, INFO_KEY_ORDER_KEY);
    }

    /**
     * The trace store these traces came from, or null where the producer did not say. Absence is not
     * a failure: an item written before anything recorded a source still carries traces worth
     * applying, and what they teach is simply not attributed.
     */
    static String readSource(final PlanBEnv env) {
        final Dbi<ByteBuffer> dbi = infoDbi(env);
        return env.read(txn -> {
            final ByteBuffer value = dbi.get(txn, key(INFO_KEY_SOURCE));
            if (value == null) {
                return null;
            }
            final byte[] bytes = new byte[value.remaining()];
            value.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        });
    }

    private static long readLong(final PlanBEnv env, final byte infoKey) {
        final Dbi<ByteBuffer> dbi = infoDbi(env);
        return env.read(txn -> {
            final ByteBuffer value = dbi.get(txn, key(infoKey));
            if (value == null) {
                throw new IllegalStateException(
                        "Queue item is missing info entry " + infoKey + "; it was not written by "
                        + QueueItemWriter.class.getSimpleName());
            }
            return value.getLong();
        });
    }

    private static Dbi<ByteBuffer> infoDbi(final PlanBEnv env) {
        return env.isReadOnly()
                ? env.openDbi(INFO_DBI_NAME)
                : env.openDbi(INFO_DBI_NAME, DbiFlags.MDB_CREATE);
    }

    private static void putLong(final Dbi<ByteBuffer> dbi,
                                final stroom.planb.impl.dao.LmdbWriter writer,
                                final byte infoKey,
                                final long value) {
        final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        valueBuffer.putLong(value).flip();
        dbi.put(writer.getWriteTxn(), key(infoKey), valueBuffer);
    }

    private static void putString(final Dbi<ByteBuffer> dbi,
                                  final stroom.planb.impl.dao.LmdbWriter writer,
                                  final byte infoKey,
                                  final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(bytes.length);
        valueBuffer.put(bytes).flip();
        dbi.put(writer.getWriteTxn(), key(infoKey), valueBuffer);
    }

    private static ByteBuffer key(final byte infoKey) {
        final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(1);
        keyBuffer.put(infoKey).flip();
        return keyBuffer;
    }
}
