/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.impl;

import stroom.docstore.api.DocumentStoreBinder;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesStore;
import stroom.planb.impl.PlanBDocumentTypes;
import stroom.planb.impl.fs.HoldingAreaMergeStrategy;
import stroom.planb.impl.fs.MergeCompletionStrategy;
import stroom.planb.impl.fs.MergeStrategy;
import stroom.planb.impl.fs.SharedFileStoreDocStore;
import stroom.planb.shared.StateType;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class PathwaysModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(TracesStore.class).to(SharedFileTracesStore.class);
        bind(TracesDocStore.class).to(TracesDocStoreImpl.class);

        // Both are documents with shards on the shared file store, so Plan B's merge finds them the
        // same way and works them with whatever strategy is bound for their type.
        Multibinder.newSetBinder(binder(), String.class, PlanBDocumentTypes.class)
                .addBinding().toInstance(TracesDoc.TYPE);
        Multibinder.newSetBinder(binder(), String.class, PlanBDocumentTypes.class)
                .addBinding().toInstance(PathwaysDoc.TYPE);

        DocumentStoreBinder.create(binder())
                .bind(PathwaysDoc.TYPE, PathwaysStore.class, PathwaysStoreImpl.class)
                .bind(TracesDoc.TYPE, TracesDocStore.class, TracesDocStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(PathwaysResourceImpl.class)
                .bind(TracesResourceImpl.class)
                .bind(TracesDocResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), SharedFileStoreDocStore.class)
                .addBinding(TracesDocStoreImpl.class);

        // A trace store accumulates spans in a holding shard and moves them on to the archive
        // buckets that queries read.
        GuiceUtil.buildMapBinder(binder(), StateType.class, MergeStrategy.class)
                .addBinding(StateType.TRACE, HoldingAreaMergeStrategy.class);

        // A pathways document's work does not arrive as batches: the traces handed over to it sit in
        // a queue of its own, and a narrowing simply falls due. Both are said through the strategy, so
        // the merge works a pathways shard on the same terms as any other.
        GuiceUtil.buildMapBinder(binder(), StateType.class, MergeStrategy.class)
                .addBinding(StateType.PATHWAYS, PathwaysProcessor.class);

        // Once a bucket has merged, a trace store linked to a Pathways document hands the traces that
        // became newly complete over to it.
        GuiceUtil.buildMapBinder(binder(), StateType.class, MergeCompletionStrategy.class)
                .addBinding(StateType.TRACE, TraceMergeCompletionStrategy.class);
    }

}
