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

package stroom.feed.client.presenter;

import stroom.data.client.presenter.DataTypeUiManager;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.client.FeedClient;
import stroom.feed.client.presenter.FeedSettingsPresenter.FeedSettingsView;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.item.client.SelectionBox;
import stroom.meta.shared.DataFormatNames;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FeedSettingsPresenter
        extends DocumentEditPresenter<FeedSettingsView, FeedDoc> {

    @SuppressWarnings("SimplifyStreamApiCallChains") // Cos GWT
    private static final List<String> FORMATS = DataFormatNames.ALL_HARD_CODED_FORMAT_NAMES.stream()
            .sorted()
            .collect(Collectors.toUnmodifiableList());
    private static final FsVolumeGroupResource VOLUME_GROUP_RESOURCE = GWT.create(FsVolumeGroupResource.class);

    private final UiConfigCache uiConfigCache;
    private final DataTypeUiManager dataTypeUiManager;
    private final RestFactory restFactory;
    private final FeedClient feedClient;

    @Inject
    public FeedSettingsPresenter(final EventBus eventBus,
                                 final FeedSettingsView view,
                                 final UiConfigCache uiConfigCache,
                                 final DataTypeUiManager dataTypeUiManager,
                                 final RestFactory restFactory,
                                 final FeedClient feedClient) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        this.dataTypeUiManager = dataTypeUiManager;
        this.restFactory = restFactory;
        this.feedClient = feedClient;

        updateEncodings();
        updateVolumeGroups();
        updateTypes();
        updateFeedStatus();

        view.getDataFormat().addItems(FORMATS);
        view.getContextFormat().addItems(FORMATS);
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

            if (!Objects.equals(dataEncoding, getEntity().getEncoding())) {
                getEntity().setEncoding(dataEncoding);
                setDirty(true);
            }
        }));
        registerHandler(getView().getContextEncoding().addValueChangeHandler(event -> {
            final String contextEncoding = ensureEncoding(getView().getContextEncoding().getValue());
            getView().getContextEncoding().setValue(contextEncoding);

            if (!Objects.equals(contextEncoding, getEntity().getContextEncoding())) {
                setDirty(true);
                getEntity().setContextEncoding(contextEncoding);
            }
        }));
        registerHandler(getView().getFeedStatus().addValueChangeHandler(event -> setDirty(true)));
        registerHandler(getView().getDataFormat().addValueChangeHandler(event -> setDirty(true)));
        registerHandler(getView().getContextFormat().addValueChangeHandler(event -> setDirty(true)));
        registerHandler(getView().getSchema().addValueChangeHandler(event -> setDirty(true)));
        registerHandler(getView().getSchemaVersion().addValueChangeHandler(event -> setDirty(true)));
        registerHandler(getView().getReceivedType().addValueChangeHandler(event -> {
            final String streamType = getView().getReceivedType().getValue();
            getView().getReceivedType().setValue(streamType);

            if (!Objects.equals(streamType, getEntity().getStreamType())) {
                setDirty(true);
                getEntity().setStreamType(streamType);
            }
        }));
        registerHandler(getView().getVolumeGroup().addValueChangeHandler(event -> {
            final String volumeGroup = getView().getVolumeGroup().getValue();
            if (!Objects.equals(volumeGroup, getEntity().getVolumeGroup())) {
                setDirty(true);
                getEntity().setVolumeGroup(volumeGroup);
            }
        }));
    }

    private void updateEncodings() {
        feedClient.fetchSupportedEncodings(result -> {
            getView().getDataEncoding().clear();
            getView().getContextEncoding().clear();

            if (NullSafe.hasItems(result)) {
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
        }, this);
    }

    private void updateVolumeGroups() {
        restFactory
                .create(VOLUME_GROUP_RESOURCE)
                .method(res -> res.find(new ExpressionCriteria()))
                .onSuccess(result -> {
                    getView().getVolumeGroup().clear();
                    getView().getVolumeGroup().setNonSelectString("");
                    NullSafe.consume(result, ResultPage::getValues, values -> {
                        for (final FsVolumeGroup volumeGroup : values) {
                            getView().getVolumeGroup().addItem(volumeGroup.getName());
                        }
                    });

                    final FeedDoc feed = getEntity();
                    if (feed != null) {
                        getView().getVolumeGroup().setValue(feed.getVolumeGroup());
                    }
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void updateTypes() {
        uiConfigCache.get(extendedUiConfig -> {
            dataTypeUiManager.getTypes(list -> {
                final SelectionBox<String> receivedTypeSelectionBox = getView().getReceivedType();
                receivedTypeSelectionBox.clear();
                if (list != null && !list.isEmpty()) {
                    receivedTypeSelectionBox.addItems(list);
                    final FeedDoc feed = getEntity();
                    if (feed != null) {
                        receivedTypeSelectionBox.setValue(feed.getStreamType());
                    }
                }
            }, this);
        }, this);
    }

    private void updateFeedStatus() {
        final SelectionBox<FeedStatus> feedStatusSelectionBox = getView().getFeedStatus();
        feedStatusSelectionBox.clear();
        feedStatusSelectionBox.addItems(FeedStatus.values());
        updateFeedStatusEnabledState();
    }

    private void updateFeedStatusEnabledState() {
        uiConfigCache.get(extendedUiConfig -> {
            final SelectionBox<FeedStatus> feedStatusSelectionBox = getView().getFeedStatus();
            final boolean isEnabled = extendedUiConfig.getReceiptCheckMode() == ReceiptCheckMode.FEED_STATUS;
            feedStatusSelectionBox.setEnabled(isEnabled);
        }, this);
    }

    @Override
    protected void onRead(final DocRef docRef, final FeedDoc feed, final boolean readOnly) {
        getView().getReference().setValue(feed.isReference());
        getView().getClassification().setText(feed.getClassification());
        getView().getDataEncoding().setValue(ensureEncoding(feed.getEncoding()));
        getView().getContextEncoding().setValue(ensureEncoding(feed.getContextEncoding()));
        getView().getReceivedType().setValue(feed.getStreamType());
        getView().getDataFormat().setValue(feed.getDataFormat());
        getView().getContextFormat().setValue(feed.getContextFormat());
        getView().getSchema().setValue(feed.getSchema());
        getView().getSchemaVersion().setValue(feed.getSchemaVersion());
        getView().getFeedStatus().setValue(feed.getStatus());
        getView().getVolumeGroup().setValue(feed.getVolumeGroup());
        updateFeedStatusEnabledState();
    }

    @Override
    protected FeedDoc onWrite(final FeedDoc feed) {
        feed.setReference(getView().getReference().getValue());
        feed.setClassification(getView().getClassification().getText());
        feed.setEncoding(ensureEncoding(getView().getDataEncoding().getValue()));
        feed.setContextEncoding(ensureEncoding(getView().getContextEncoding().getValue()));
        feed.setStreamType(getView().getReceivedType().getValue());
        feed.setDataFormat(getView().getDataFormat().getValue());
        feed.setContextFormat(getView().getContextFormat().getValue());
        feed.setSchema(getView().getSchema().getValue());
        feed.setSchemaVersion(getView().getSchemaVersion().getValue());
        // Set the process stage.
        feed.setStatus(getView().getFeedStatus().getValue());
        feed.setVolumeGroup(getView().getVolumeGroup().getValue());
        return feed;
    }

    private String ensureEncoding(final String encoding) {
        if (NullSafe.isBlankString(encoding)) {
            return "UTF-8";
        }
        return encoding;
    }


    // --------------------------------------------------------------------------------


    public interface FeedSettingsView extends View {

        TextBox getClassification();

        CustomCheckBox getReference();

        SelectionBox<String> getDataEncoding();

        SelectionBox<String> getContextEncoding();

        SelectionBox<String> getReceivedType();

        SelectionBox<String> getDataFormat();

        SelectionBox<String> getContextFormat();

        TextBox getSchema();

        TextBox getSchemaVersion();

        SelectionBox<FeedStatus> getFeedStatus();

        SelectionBox<String> getVolumeGroup();
    }
}
