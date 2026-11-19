/*
 * Copyright 2020 Crown Copyright
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

package stroom.index.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.QueryConfig;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.stream.Collectors;

/// A Lucene index's Settings tab ([IndexSettings]): it fetches the volume groups to choose from,
/// offers the pipelines with the tags configured for an index's picker, and deletes all the index's
/// shards when asked, once the user confirms.
public class IndexSettingsController extends Controller {

    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);
    private static final IndexResource INDEX_RESOURCE =
            GWT.create(IndexResource.class);

    private final IndexSettings view;
    private final RestFactory restFactory;
    private final DocSelectionController pipeline;
    private LuceneIndexDoc index;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The tab.
    /// @param docSelectionProvider Makes the pipeline's picker's controller.
    /// @param restFactory          Fetches the volume groups and resets the index.
    /// @param uiConfigCache        Gives the tags of the pipelines offered.
    @Inject
    public IndexSettingsController(final EventBus eventBus,
                                   final IndexSettings view,
                                   final Provider<DocSelectionController> docSelectionProvider,
                                   final RestFactory restFactory,
                                   final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);

        pipeline = make(docSelectionProvider);
        pipeline.attach(view.getDefaultExtractionPipelineBox());
        pipeline.setIncludedTypes(PipelineDoc.TYPE);
        pipeline.setRequiredPermissions(DocumentPermission.VIEW);

        // Filter the pipeline picker by tags, if configured
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null && !isDisposed()) {
                NullSafe.consume(
                        extendedUiConfig.getQuery(),
                        QueryConfig::getIndexPipelineSelectorIncludedTags,
                        ExplorerTreeFilter::createTagQuickFilterInput,
                        pipeline::setQuickFilter);
            }
        }, taskMonitor());

        view.setOnChange(this::markChanged);
        view.setOnReset(this::reset);
        own(pipeline.addSelectionListener(selection -> markChanged()));
        watch(Contexts.READ_ONLY, this::setReadOnly);
    }

    /// Shows an index's settings.
    ///
    /// @param docRef The index.
    /// @param index  Its settings.
    public void read(final DocRef docRef, final LuceneIndexDoc index) {
        this.index = index;
        view.read(index);
        updateGroupList(index.getVolumeGroupName());
        pipeline.setSelectedEntityReference(index.getDefaultExtractionPipeline(), true);
    }

    /// Writes the settings shown into a copy of an index.
    ///
    /// @param index The index.
    /// @return A copy of it with the settings shown.
    public LuceneIndexDoc write(final LuceneIndexDoc index) {
        return view.write(index.copy())
                .defaultExtractionPipeline(pipeline.getSelectedEntityReference())
                .build();
    }

    private void reset() {
        if (index != null) {
            final String uuid = index.getUuid();
            ConfirmEvent.fire(this,
                    "This will remove all entries from the index. Are you sure you want to do this?",
                    ok -> {
                        if (ok) {
                            restFactory
                                    .create(INDEX_RESOURCE)
                                    .method(res -> res.reset(uuid))
                                    .onSuccess(count -> AlertEvent.fireInfo(
                                            this,
                                            count == null || count == 0
                                                    ? "Index reset. It had no shards to delete."
                                                    : "Index reset. " + count + " shard"
                                                      + (count == 1 ? " is" : "s are")
                                                      + " marked for deletion.",
                                            null))
                                    .onFailure(throwable -> AlertEvent.fireError(
                                            this,
                                            "Failed to reset index: " + throwable.getMessage(),
                                            null))
                                    .owner(this)
                                    .exec();
                        }
                    });
        }
    }

    private void updateGroupList(final String selected) {
        restFactory
                .create(INDEX_VOLUME_GROUP_RESOURCE)
                .method(res -> res.find(new ExpressionCriteria()))
                .onSuccess(result -> {
                    final List<String> volumeGroupNames = result
                            .getValues()
                            .stream()
                            .map(IndexVolumeGroup::getName)
                            .collect(Collectors.toList());
                    view.setVolumeGroups(volumeGroupNames, selected);
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void setReadOnly(final boolean readOnly) {
        view.setReadOnly(readOnly);
        pipeline.setReadOnly(readOnly);
    }

}
