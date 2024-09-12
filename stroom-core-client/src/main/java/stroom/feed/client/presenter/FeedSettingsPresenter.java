/*
 * Copyright 2017 Crown Copyright
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

package stroom.feed.client.presenter;

import stroom.data.client.presenter.DataTypeUiManager;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.client.presenter.FeedSettingsPresenter.FeedSettingsView;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.feed.shared.FeedResource;
import stroom.item.client.SelectionBox;
import stroom.util.shared.EqualsUtil;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class FeedSettingsPresenter
        extends DocumentEditPresenter<FeedSettingsView, FeedDoc> {

    private static final FeedResource FEED_RESOURCE = GWT.create(FeedResource.class);
    private static final FsVolumeGroupResource VOLUME_GROUP_RESOURCE = GWT.create(FsVolumeGroupResource.class);

    private final DataTypeUiManager dataTypeUiManager;
    private final RestFactory restFactory;

    @Inject
    public FeedSettingsPresenter(final EventBus eventBus,
                                 final FeedSettingsView view,
                                 final DataTypeUiManager dataTypeUiManager,
                                 final RestFactory restFactory) {
        super(eventBus, view);
        this.dataTypeUiManager = dataTypeUiManager;
        this.restFactory = restFactory;

        updateEncodings();
        updateVolumeGroups();
        updateTypes();
        view.getFeedStatus().addItems(FeedStatus.values());
    }

    @Override
    protected void onBind() {
        super.onBind();
        // Add listeners for dirty events.
        final ValueChangeHandler<Boolean> checkHandler = event -> setDirty(true);
        registerHandler(getView().getClassification().addValueChangeHandler(e -> setDirty(true)));
        registerHandler(getView().getReference().addValueChangeHandler(checkHandler));
        registerHandler(getView().getDataEncoding().addValueChangeHandler(event -> {
            final String dataEncoding = ensureEncoding(getView().getDataEncoding().getValue());
            getView().getDataEncoding().setValue(dataEncoding);

            if (!EqualsUtil.isEquals(dataEncoding, getEntity().getEncoding())) {
                getEntity().setEncoding(dataEncoding);
                setDirty(true);
            }
        }));
        registerHandler(getView().getContextEncoding().addValueChangeHandler(event -> {
            final String contextEncoding = ensureEncoding(getView().getContextEncoding().getValue());
            getView().getContextEncoding().setValue(contextEncoding);

            if (!EqualsUtil.isEquals(contextEncoding, getEntity().getContextEncoding())) {
                setDirty(true);
                getEntity().setContextEncoding(contextEncoding);
            }
        }));
        registerHandler(getView().getFeedStatus().addValueChangeHandler(event -> setDirty(true)));
        registerHandler(getView().getReceivedType().addValueChangeHandler(event -> {
            final String streamType = getView().getReceivedType().getValue();
            getView().getReceivedType().setValue(streamType);

            if (!EqualsUtil.isEquals(streamType, getEntity().getStreamType())) {
                setDirty(true);
                getEntity().setStreamType(streamType);
            }
        }));
        registerHandler(getView().getVolumeGroup().addValueChangeHandler(event -> {
            final String volumeGroup = getView().getVolumeGroup().getValue();
            if (!EqualsUtil.isEquals(volumeGroup, getEntity().getVolumeGroup())) {
                setDirty(true);
                getEntity().setVolumeGroup(volumeGroup);
            }
        }));
    }

    private void updateEncodings() {
        restFactory
                .create(FEED_RESOURCE)
                .method(FeedResource::fetchSupportedEncodings)
                .onSuccess(result -> {
                    getView().getDataEncoding().clear();
                    getView().getContextEncoding().clear();

                    if (result != null && result.size() > 0) {
                        for (final String encoding : result) {
                            getView().getDataEncoding().addItem(encoding);
                            getView().getContextEncoding().addItem(encoding);
                        }
                    }

                    final FeedDoc feed = getEntity();
                    if (feed != null) {
                        getView().getDataEncoding().setValue(ensureEncoding(feed.getEncoding()));
                        getView().getContextEncoding().setValue(ensureEncoding(feed.getContextEncoding()));
                    }
                })
                .taskHandlerFactory(this)
                .exec();
    }

    private void updateVolumeGroups() {
        restFactory
                .create(VOLUME_GROUP_RESOURCE)
                .method(res -> res.find(new ExpressionCriteria()))
                .onSuccess(result -> {
                    getView().getVolumeGroup().clear();
                    getView().getVolumeGroup().setNonSelectString("");
                    if (result != null && result.getValues() != null) {
                        for (final FsVolumeGroup volumeGroup : result.getValues()) {
                            getView().getVolumeGroup().addItem(volumeGroup.getName());
                        }
                    }

                    final FeedDoc feed = getEntity();
                    if (feed != null) {
                        getView().getVolumeGroup().setValue(feed.getVolumeGroup());
                    }
                })
                .taskHandlerFactory(this)
                .exec();
    }

    private void updateTypes() {
        dataTypeUiManager.getTypes(list -> {
            getView().getReceivedType().clear();
            if (list != null && !list.isEmpty()) {
                getView().getReceivedType().addItems(list);
                final FeedDoc feed = getEntity();
                if (feed != null) {
                    getView().getReceivedType().setValue(feed.getStreamType());
                }
            }
        }, this);
    }

    @Override
    protected void onRead(final DocRef docRef, final FeedDoc feed, final boolean readOnly) {
        getView().getReference().setValue(feed.isReference());
        getView().getClassification().setText(feed.getClassification());
        getView().getDataEncoding().setValue(ensureEncoding(feed.getEncoding()));
        getView().getContextEncoding().setValue(ensureEncoding(feed.getContextEncoding()));
        getView().getReceivedType().setValue(feed.getStreamType());
        getView().getFeedStatus().setValue(feed.getStatus());
        getView().getVolumeGroup().setValue(feed.getVolumeGroup());
    }

    @Override
    protected FeedDoc onWrite(final FeedDoc feed) {
        feed.setReference(getView().getReference().getValue());
        feed.setClassification(getView().getClassification().getText());
        feed.setEncoding(ensureEncoding(getView().getDataEncoding().getValue()));
        feed.setContextEncoding(ensureEncoding(getView().getContextEncoding().getValue()));
        feed.setStreamType(getView().getReceivedType().getValue());
        feed.setVolumeGroup(getView().getVolumeGroup().getValue());

        // Set the process stage.
        feed.setStatus(getView().getFeedStatus().getValue());
        return feed;
    }

    private String ensureEncoding(final String encoding) {
        if (encoding == null || encoding.trim().length() == 0) {
            return "UTF-8";
        }
        return encoding;
    }

    public interface FeedSettingsView extends View {

        TextBox getClassification();

        CustomCheckBox getReference();

        SelectionBox<String> getDataEncoding();

        SelectionBox<String> getContextEncoding();

        SelectionBox<String> getReceivedType();

        SelectionBox<FeedStatus> getFeedStatus();

        SelectionBox<String> getVolumeGroup();
    }
}
