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

import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.tree.tiny.Statistics;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyTree;

import java.lang.reflect.Field;

/**
 * A {@link TreeModel} that hands out one builder over and over, so a stream of small documents
 * shares a single source tree instead of allocating one each.
 * <p>
 * <strong>Research code, not production code.</strong> See the module README.
 * <p>
 * This is the supported hook that makes the experiment possible: {@code TreeModel} is public and
 * abstract, {@code Controller.setModel(TreeModel)} is public, and {@code Controller.makeBuilder()}
 * calls {@code treeModel.makeBuilder(pipe)} virtually — so the builder returned here is the one the
 * JAXP {@code TransformerHandler} path will use. Install it with:
 * <pre>
 * final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
 * transformer.getUnderlyingController().setModel(new ReusableTinyTreeModel());
 * </pre>
 * <p>
 * Saxon already does half the work: {@code TinyBuilder.open()} only allocates when its tree is
 * null. What gets in the way is {@code TinyBuilder.reset()}, which
 * {@code TransformerHandlerImpl.endDocument()} calls after every document and which sets that
 * private field back to null. So the builder below keeps its own reference and puts it back.
 * <p>
 * Not thread safe, deliberately: one model instance belongs to one transformer driven by one thread.
 */
public class ReusableTinyTreeModel extends TreeModel {

    /**
     * Sized for a handful of nodes rather than Saxon's default of 4000 nodes and 4000 characters.
     * The arrays grow on demand and, because the tree is never discarded, they stay grown, so this
     * only needs to be a sensible floor rather than an accurate estimate.
     * <p>
     * It also has to be big enough that {@code TinyTree.condense()} leaves the arrays alone: it
     * trims only when {@code numberOfNodes * 3 < nodeKind.length}.
     */
    private static final Statistics SMALL_DOCUMENT_STATISTICS = new Statistics(16, 16, 4, 64);

    private final Statistics statistics;

    private ReusingBuilder builder;

    public ReusableTinyTreeModel() {
        this(SMALL_DOCUMENT_STATISTICS);
    }

    public ReusableTinyTreeModel(final Statistics statistics) {
        this.statistics = statistics;
    }

    @Override
    public Builder makeBuilder(final PipelineConfiguration pipe) {
        if (builder == null) {
            builder = new ReusingBuilder(pipe, statistics);
        } else {
            // The pipeline configuration is per document even though the builder is not.
            builder.setPipelineConfiguration(pipe);
        }
        return builder;
    }

    @Override
    public String getName() {
        return "ReusableTinyTree";
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public boolean isSchemaAware() {
        return false;
    }

    /**
     * How many documents have been built into the shared tree.
     */
    public long getDocumentsBuilt() {
        return builder == null
                ? 0
                : builder.documentsBuilt;
    }

    /**
     * The capacity the shared tree's arrays have settled at, or 0 if nothing has been built.
     */
    public int getNodeCapacity() {
        return builder == null || builder.retainedTree == null
                ? 0
                : TinyTreeReuse.getNodeCapacity(builder.retainedTree);
    }

    /**
     * A {@link TinyBuilder} that keeps its tree between documents.
     */
    private static final class ReusingBuilder extends TinyBuilder {

        private static final Field TREE_FIELD;

        static {
            try {
                TREE_FIELD = TinyBuilder.class.getDeclaredField("tree");
                TREE_FIELD.setAccessible(true);
            } catch (final NoSuchFieldException e) {
                throw new ExceptionInInitializerError(
                        "TinyBuilder.tree is absent; this experiment is pinned to Saxon-HE 9.9.x");
            }
        }

        private final Statistics statistics;

        private TinyTree retainedTree;
        private long documentsBuilt;

        private ReusingBuilder(final PipelineConfiguration pipe, final Statistics statistics) {
            super(pipe);
            this.statistics = statistics;
            super.setStatistics(statistics);
        }

        /**
         * Ignores Saxon's attempt to impose the shared, self-tuning statistics.
         * <p>
         * {@code TransformerHandlerImpl}'s constructor calls
         * {@code setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS)} on every
         * builder it is given. That object starts at 4000 nodes and 4000 characters, which is
         * exactly the sizing this experiment exists to avoid.
         */
        @Override
        public void setStatistics(final Statistics ignored) {
            super.setStatistics(statistics);
        }

        /**
         * Installs the retained tree so {@code TinyBuilder.open()} finds it non-null and skips
         * allocating.
         */
        @Override
        public void open() {
            if (retainedTree == null) {
                retainedTree = new TinyTree(getConfiguration(), statistics);
            } else {
                TinyTreeReuse.resetForReuse(retainedTree);
            }
            setTreeField(retainedTree);
            documentsBuilt++;
            super.open();
        }

        /**
         * Lets Saxon reset its own per-document state, then keeps hold of the tree it discarded.
         */
        @Override
        public void reset() {
            super.reset();
            // super.reset() also swaps in TEMPORARY_TREE_STATISTICS; put ours back.
            super.setStatistics(statistics);
        }

        private void setTreeField(final TinyTree tree) {
            try {
                TREE_FIELD.set(this, tree);
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException("Unable to install the retained TinyTree", e);
            }
        }
    }
}
