/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.benchmark;

import net.sf.saxon.tree.tiny.AppendableCharSequence;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * Returns a {@link TinyTree}'s counters to zero so the next document can be written over the top of
 * the last one, keeping every array that has already been allocated.
 * <p>
 * <strong>Research code, not production code.</strong> See the module README.
 * <p>
 * This has to work by reflection. {@code TinyTree} is {@code final}, so it cannot be subclassed, and
 * its state is {@code protected}, so it is not reachable from another package by normal means. The
 * obvious alternative — putting a helper class in {@code net.sf.saxon.tree.tiny} — is blocked
 * because <em>the Saxon-HE jar is signed</em> (it carries {@code META-INF/TE-050AC.SF}); the JVM
 * refuses to load an unsigned class into a signed package with
 * "signer information does not match signer information of other classes in the same package".
 * Reflection is legal here only because Saxon is on the class path, so it lives in the unnamed
 * module where {@code setAccessible} is unrestricted.
 * <p>
 * The cost is a dozen field writes per document, which is nothing next to the allocation being
 * avoided.
 *
 * <p><strong>Correctness caveats.</strong> {@code idTable}, {@code lineNumbers},
 * {@code columnNumbers} and {@code systemIdMap} are cleared here too where reflection allows, but a
 * stylesheet using {@code id()}, {@code xsl:key} or line numbers is still the risky case. Reuse is
 * only sound because one document's transform completes before the next is opened: a stylesheet
 * returning source nodes rather than serialising them would be left pointing into a tree that is
 * about to be overwritten.
 */
public final class TinyTreeReuse {

    /**
     * Integer counters that must go back to zero.
     */
    private static final Field[] INT_COUNTERS = fields(
            "numberOfNodes", "numberOfAttributes", "numberOfNamespaces");

    /**
     * Lazily built indexes and per-document sets that would otherwise alias the new document's
     * nodes. Nulling them makes Saxon rebuild on demand.
     */
    private static final Field[] NULLABLE = fields(
            "prior", "idRefElements", "idRefAttributes", "nilledElements", "defaultedAttributes",
            "topWithinEntity", "idTable", "lineNumbers", "columnNumbers", "systemIdMap", "copiedFrom");

    private static final Field USES_NAMESPACES = field("usesNamespaces");
    private static final Field CHAR_BUFFER = field("charBuffer");
    private static final Field COMMENT_BUFFER = field("commentBuffer");
    private static final Field EXTERNAL_NODES = field("externalNodes");
    private static final Field ENTITY_TABLE = field("entityTable");
    private static final Field NODE_KIND = field("nodeKind");

    private TinyTreeReuse() {
        // Utility class.
    }

    /**
     * Resets <code>tree</code> so it can be built into again.
     * <p>
     * Only counters and lazily built indexes are cleared; every array is kept, which is the entire
     * point. Saxon indexes those arrays by node, attribute and namespace number, so entries beyond
     * the reset counters are never read.
     */
    public static void resetForReuse(final TinyTree tree) {
        try {
            for (final Field counter : INT_COUNTERS) {
                counter.setInt(tree, 0);
            }
            for (final Field nullable : NULLABLE) {
                if (nullable != null) {
                    nullable.set(tree, null);
                }
            }
            USES_NAMESPACES.setBoolean(tree, false);

            final Object charBuffer = CHAR_BUFFER.get(tree);
            if (charBuffer instanceof final AppendableCharSequence acs) {
                acs.setLength(0);
            }
            final Object commentBuffer = COMMENT_BUFFER.get(tree);
            if (commentBuffer instanceof final FastStringBuffer fsb) {
                fsb.setLength(0);
            }
            final Object externalNodes = EXTERNAL_NODES.get(tree);
            if (externalNodes instanceof final Collection<?> c) {
                c.clear();
            }
            final Object entityTable = ENTITY_TABLE.get(tree);
            if (entityTable instanceof final Map<?, ?> m) {
                m.clear();
            }
            // The prefix pool is deliberately kept: prefix codes are stable across documents, and
            // not rebuilding it is one of the savings being measured.
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Unable to reset the TinyTree for reuse", e);
        }
    }

    /**
     * The current capacity of the node arrays, i.e. the high-water mark that reuse settles at.
     */
    public static int getNodeCapacity(final TinyTree tree) {
        try {
            final byte[] nodeKind = (byte[]) NODE_KIND.get(tree);
            return nodeKind == null
                    ? 0
                    : nodeKind.length;
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Unable to read the TinyTree node capacity", e);
        }
    }

    private static Field[] fields(final String... names) {
        final Field[] result = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = field(names[i]);
        }
        return result;
    }

    /**
     * Returns an accessible field, or null if this Saxon build does not have it. Tolerating absence
     * keeps the experiment from exploding on a different Saxon patch version, at the cost of
     * silently not clearing something — acceptable for research code, not for production.
     */
    private static Field field(final String name) {
        try {
            final Field f = TinyTree.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (final NoSuchFieldException e) {
            return null;
        }
    }
}
