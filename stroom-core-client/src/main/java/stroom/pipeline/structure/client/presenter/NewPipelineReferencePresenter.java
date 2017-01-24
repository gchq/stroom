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

package stroom.pipeline.structure.client.presenter;

import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.entity.shared.SharedDocRef;
import stroom.query.api.DocRef;
import stroom.entity.shared.EntityReferenceFindAction;
import stroom.entity.shared.ResultList;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.explorer.shared.ExplorerData;
import stroom.feed.shared.Feed;
import stroom.item.client.StringListBox;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.StreamType.Purpose;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class NewPipelineReferencePresenter
        extends MyPresenterWidget<NewPipelineReferencePresenter.NewPipelineReferenceView>
        implements HasRead<PipelineReference>, HasWrite<PipelineReference> {
    private final EntityDropDownPresenter pipelinePresenter;
    private final EntityDropDownPresenter feedPresenter;
    private final StringListBox streamTypesWidget;
    private boolean dirty;

    @Inject
    public NewPipelineReferencePresenter(final EventBus eventBus, final NewPipelineReferenceView view,
            final EntityDropDownPresenter pipelinePresenter, final EntityDropDownPresenter feedPresenter,
            final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;
        this.feedPresenter = feedPresenter;

        pipelinePresenter.setIncludedTypes(PipelineEntity.ENTITY_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.USE);
        feedPresenter.setIncludedTypes(Feed.ENTITY_TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        streamTypesWidget = new StringListBox();
        final FindStreamTypeCriteria findStreamTypeCriteria = new FindStreamTypeCriteria();
        findStreamTypeCriteria.obtainPurpose().add(Purpose.RAW);
        findStreamTypeCriteria.obtainPurpose().add(Purpose.PROCESSED);
        findStreamTypeCriteria.obtainPurpose().add(Purpose.CONTEXT);
        dispatcher.execute(new EntityReferenceFindAction<>(findStreamTypeCriteria),
                new AsyncCallbackAdaptor<ResultList<SharedDocRef>>() {
                    @Override
                    public void onSuccess(final ResultList<SharedDocRef> result) {
                        if (result != null && result.size() > 0) {
                            for (final SharedDocRef docRef : result) {
                                streamTypesWidget.addItem(docRef.getName());
                            }
                        }
                    }
                });

        pipelinePresenter.getWidget().getElement().getStyle().setMarginBottom(0, Unit.PX);
        getView().setPipelineView(pipelinePresenter.getView());

        feedPresenter.getWidget().getElement().getStyle().setMarginBottom(0, Unit.PX);
        getView().setFeedView(feedPresenter.getView());

        streamTypesWidget.getElement().getStyle().setMarginBottom(0, Unit.PX);
        getView().setStreamTypeWidget(streamTypesWidget);
    }

    @Override
    public void read(final PipelineReference pipelineReference) {
        getView().setElement(pipelineReference.getElement());

        pipelinePresenter.setSelectedEntityReference(pipelineReference.getPipeline());
        feedPresenter.setSelectedEntityReference(pipelineReference.getFeed());
        streamTypesWidget.setSelected(pipelineReference.getStreamType());

        pipelinePresenter.addDataSelectionHandler(new DataSelectionHandler<ExplorerData>() {
            @Override
            public void onSelection(final DataSelectionEvent<ExplorerData> event) {
                final DocRef selection = pipelinePresenter.getSelectedEntityReference();
                if ((pipelineReference.getPipeline() == null && selection != null)
                        || (pipelineReference.getPipeline() != null
                                && !pipelineReference.getPipeline().equals(selection))) {
                    setDirty(true);
                }
            }
        });
        feedPresenter.addDataSelectionHandler(new DataSelectionHandler<ExplorerData>() {
            @Override
            public void onSelection(final DataSelectionEvent<ExplorerData> event) {
                final DocRef selection = feedPresenter.getSelectedEntityReference();
                if ((pipelineReference.getFeed() == null && selection != null)
                        || (pipelineReference.getFeed() != null && !pipelineReference.getFeed().equals(selection))) {
                    setDirty(true);
                }
            }
        });
        streamTypesWidget.addChangeHandler(new ChangeHandler() {
            @Override
            public void onChange(final ChangeEvent event) {
                final String selection = streamTypesWidget.getSelected();
                if ((pipelineReference.getStreamType() == null && selection != null)
                        || (pipelineReference.getStreamType() != null
                        && !pipelineReference.getStreamType().equals(selection))) {
                    setDirty(true);
                }
            }
        });
    }

    @Override
    public void write(final PipelineReference pipelineReference) {
        pipelineReference.setPipeline(pipelinePresenter.getSelectedEntityReference());
        pipelineReference.setFeed(feedPresenter.getSelectedEntityReference());
        pipelineReference.setStreamType(streamTypesWidget.getSelected());
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

        void setStreamTypeWidget(Widget widget);
    }
}
