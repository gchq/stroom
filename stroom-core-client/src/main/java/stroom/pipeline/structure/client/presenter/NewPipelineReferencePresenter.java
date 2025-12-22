/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.structure.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.feed.shared.FeedDoc;
import stroom.item.client.SelectionBox;
import stroom.meta.shared.MetaResource;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.planb.shared.PlanBDoc;
import stroom.security.shared.DocumentPermission;
import stroom.state.shared.StateDoc;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class NewPipelineReferencePresenter
        extends MyPresenterWidget<NewPipelineReferencePresenter.NewPipelineReferenceView>
        implements Focus {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final DocSelectionBoxPresenter pipelinePresenter;
    private final DocSelectionBoxPresenter feedPresenter;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final SelectionBox<String> dataTypeWidget;
    private boolean dirty;
    private boolean initialised;
    private PipelineReference currentPipelineReference;

    @Inject
    public NewPipelineReferencePresenter(final EventBus eventBus,
                                         final NewPipelineReferenceView view,
                                         final DocSelectionBoxPresenter pipelinePresenter,
                                         final DocSelectionBoxPresenter feedPresenter,
                                         final RestFactory restFactory,
                                         final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;
        this.feedPresenter = feedPresenter;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;

        // TODO : @66 FIX TEMPORARY ABUSE OF PIPELINE REF
        pipelinePresenter.setIncludedTypes(PipelineDoc.TYPE, StateDoc.TYPE, PlanBDoc.TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermission.USE);

        feedPresenter.setIncludedTypes(FeedDoc.TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermission.USE);

        pipelinePresenter.getWidget().getElement().getStyle().setMarginBottom(0, Unit.PX);
        getView().setPipelineView(pipelinePresenter.getView());

        feedPresenter.getWidget().getElement().getStyle().setMarginBottom(0, Unit.PX);
        getView().setFeedView(feedPresenter.getView());

        dataTypeWidget = new SelectionBox<>();
        dataTypeWidget.getElement().getStyle().setMarginBottom(0, Unit.PX);
        getView().setTypeWidget(dataTypeWidget);
    }

    @Override
    public void focus() {
        pipelinePresenter.focus();
    }

    public void read(final PipelineReference pipelineReference) {
        this.currentPipelineReference = pipelineReference;

        // Filter the pipeline picker by tags, if configured
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                NullSafe.consume(
                        extendedUiConfig.getReferencePipelineSelectorIncludedTags(),
                        ExplorerTreeFilter::createTagQuickFilterInput,
                        pipelinePresenter::setQuickFilter);
            }
        }, this);

        getView().setElement(pipelineReference.getElement());

        pipelinePresenter.setSelectedEntityReference(pipelineReference.getPipeline(), true);
        feedPresenter.setSelectedEntityReference(pipelineReference.getFeed(), true);
        updateDataTypes(pipelineReference.getStreamType());

        pipelinePresenter.addDataSelectionHandler(event -> {
            if (initialised) {
                final DocRef selection = pipelinePresenter.getSelectedEntityReference();
                if ((pipelineReference.getPipeline() == null && selection != null)
                    || (pipelineReference.getPipeline() != null
                        && !pipelineReference.getPipeline().equals(selection))) {
                    setDirty(true);
                }
            }
        });
        feedPresenter.addDataSelectionHandler(event -> {
            if (initialised) {
                final DocRef selection = feedPresenter.getSelectedEntityReference();
                if ((pipelineReference.getFeed() == null && selection != null)
                    || (pipelineReference.getFeed() != null && !pipelineReference.getFeed().equals(selection))) {
                    setDirty(true);
                }
            }
        });
        dataTypeWidget.addValueChangeHandler(event -> {
            if (initialised) {
                final String selection = dataTypeWidget.getValue();
                if ((pipelineReference.getStreamType() == null && selection != null)
                    || (pipelineReference.getStreamType() != null
                        && !pipelineReference.getStreamType().equals(selection))) {
                    setDirty(true);
                }
            }
        });
    }

    public PipelineReference write() {
        return new PipelineReference.Builder(currentPipelineReference)
                .pipeline(pipelinePresenter.getSelectedEntityReference())
                .feed(feedPresenter.getSelectedEntityReference())
                .streamType(dataTypeWidget.getValue())
                .build();
    }

    private void updateDataTypes(final String selectedDataType) {
        dataTypeWidget.clear();

        restFactory
                .create(META_RESOURCE)
                .method(MetaResource::getTypes)
                .onSuccess(result -> {
                    if (result != null) {
                        dataTypeWidget.addItems(result);
                    }

                    if (selectedDataType != null) {
                        dataTypeWidget.setValue(selectedDataType);
                    }

                    initialised = true;
                })
                .taskMonitorFactory(this)
                .exec();
    }

    public boolean isDirty() {
        return dirty;
    }

    private void setDirty(final boolean dirty) {
        this.dirty = dirty;
    }

    public interface NewPipelineReferenceView extends View {

        void setElement(String element);

        void setPipelineView(View view);

        void setFeedView(View view);

        void setTypeWidget(Widget widget);
    }
}
