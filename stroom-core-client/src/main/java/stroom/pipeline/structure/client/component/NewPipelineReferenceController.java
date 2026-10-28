/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.structure.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.MetaResource;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.planb.shared.PlanBDoc;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;

/// The New and Edit Pipeline Reference dialog ([NewPipelineReference]) of a pipeline element, made
/// for one use: the reference loader pipeline (or a Plan B or SQL temporal store), feed and stream
/// type it loads from.
public class NewPipelineReferenceController extends Controller {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final NewPipelineReference view;
    private final DocSelectionController pipeline;
    private final DocSelectionController feed;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the pickers' controllers.
    /// @param restFactory          Fetches the stream types.
    /// @param uiConfigCache        Gives the tags the pipelines are filtered by.
    @Inject
    public NewPipelineReferenceController(final EventBus eventBus,
                                          final NewPipelineReference view,
                                          final Provider<DocSelectionController> docSelectionProvider,
                                          final RestFactory restFactory,
                                          final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        initWidget(view);

        pipeline = make(docSelectionProvider);
        pipeline.attach(view.getPipelineBox());
        // TODO : @66 FIX TEMPORARY ABUSE OF PIPELINE REF
        pipeline.setIncludedTypes(PipelineDoc.TYPE, PlanBDoc.TYPE, SqlTemporalStoreDoc.TYPE);
        pipeline.setRequiredPermissions(DocumentPermission.USE);

        feed = make(docSelectionProvider);
        feed.attach(view.getFeedBox());
        feed.setIncludedTypes(FeedDoc.TYPE);
        feed.setRequiredPermissions(DocumentPermission.USE);
    }

    /// Edits a pipeline reference in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener            What shows it.
    /// @param caption           The dialog's caption, e.g. "New Pipeline Reference".
    /// @param pipelineReference The reference.
    /// @param onOk              Given the reference entered and the dialog's OK, which it hides or
    ///                          resets.
    /// @param onCancel          Run when the dialog is cancelled.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final PipelineReference pipelineReference,
                     final BiConsumer<PipelineReference, HidePopupRequestEvent> onOk,
                     final Runnable onCancel) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption(caption)
                .onShow(e -> {
                    read(pipelineReference);
                    pipeline.focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(new PipelineReference.Builder(pipelineReference)
                                .pipeline(pipeline.getSelectedEntityReference())
                                .feed(feed.getSelectedEntityReference())
                                .streamType(view.getTypeBox().getValue())
                                .build(), e);
                    } else {
                        onCancel.run();
                        e.hide();
                    }
                })
                .fire();
    }

    private void read(final PipelineReference pipelineReference) {
        // Filter the pipeline picker by tags, if configured
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null && !isDisposed()) {
                NullSafe.consume(
                        extendedUiConfig.getReferencePipelineSelectorIncludedTags(),
                        ExplorerTreeFilter::createTagQuickFilterInput,
                        pipeline::setQuickFilter);
            }
        }, taskMonitor());

        view.setElement(pipelineReference.getElement());
        pipeline.setSelectedEntityReference(pipelineReference.getPipeline(), true);
        feed.setSelectedEntityReference(pipelineReference.getFeed(), true);

        view.getTypeBox().clear();
        restFactory
                .create(META_RESOURCE)
                .method(MetaResource::getTypes)
                .onSuccess(result -> {
                    if (result != null) {
                        view.getTypeBox().addItems(result);
                    }
                    if (pipelineReference.getStreamType() != null) {
                        view.getTypeBox().setValue(pipelineReference.getStreamType());
                    }
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

}
