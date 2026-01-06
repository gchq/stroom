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

package stroom.processor.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.client.FeedClient;
import stroom.feed.shared.FeedDoc;
import stroom.item.client.SelectionBox;
import stroom.meta.shared.MetaResource;
import stroom.processor.shared.FeedDependency;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;

public class EditFeedDependencyPresenter
        extends MyPresenterWidget<EditFeedDependencyPresenter.EditFeedDependencyView>
        implements Focus {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final DocSelectionBoxPresenter feedPresenter;
    private final RestFactory restFactory;
    private final FeedClient feedClient;
    private final SelectionBox<String> dataTypeWidget;
    private String uuid;
    private boolean dirty;
    private boolean initialised;

    @Inject
    public EditFeedDependencyPresenter(final EventBus eventBus,
                                       final EditFeedDependencyView view,
                                       final DocSelectionBoxPresenter feedPresenter,
                                       final RestFactory restFactory,
                                       final FeedClient feedClient) {
        super(eventBus, view);
        this.feedPresenter = feedPresenter;
        this.restFactory = restFactory;
        this.feedClient = feedClient;

        feedPresenter.setIncludedTypes(FeedDoc.TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermission.USE);

        feedPresenter.getWidget().getElement().getStyle().setMarginBottom(0, Unit.PX);
        getView().setFeedView(feedPresenter.getView());

        dataTypeWidget = new SelectionBox<>();
        dataTypeWidget.getElement().getStyle().setMarginBottom(0, Unit.PX);
        getView().setTypeWidget(dataTypeWidget);
    }

    @Override
    public void focus() {
        feedPresenter.focus();
    }

    public void read(final FeedDependency feedDependency) {
        uuid = feedDependency.getUuid();

        // Fetch the doc ref for the supplied feed name.
        feedClient.getDocRefForName(feedDependency.getFeedName(), docRef ->
                feedPresenter.setSelectedEntityReference(docRef, true), this);

        updateDataTypes(feedDependency.getStreamType());

        feedPresenter.addDataSelectionHandler(event -> {
            if (initialised) {
                final String feedName = NullSafe.get(feedPresenter.getSelectedEntityReference(), DocRef::getName);
                if (!Objects.equals(feedDependency.getFeedName(), feedName)) {
                    setDirty(true);
                }
            }
        });
        dataTypeWidget.addValueChangeHandler(event -> {
            if (initialised) {
                final String streamType = dataTypeWidget.getValue();
                if (!Objects.equals(feedDependency.getStreamType(), streamType)) {
                    setDirty(true);
                }
            }
        });
    }

    public FeedDependency write() {
        final String feedName = NullSafe.get(feedPresenter.getSelectedEntityReference(), DocRef::getName);
        return new FeedDependency(uuid, feedName, dataTypeWidget.getValue());
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

    public interface EditFeedDependencyView extends View {

        void setFeedView(View view);

        void setTypeWidget(Widget widget);
    }
}
