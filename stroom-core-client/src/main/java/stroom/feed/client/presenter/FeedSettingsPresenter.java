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
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.feed.client.presenter.FeedSettingsPresenter.FeedSettingsView;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.feed.shared.FeedResource;
import stroom.item.client.ItemListBox;
import stroom.item.client.StringListBox;
import stroom.util.shared.EqualsUtil;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.InputEvent;
import com.google.gwt.event.dom.client.InputHandler;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class FeedSettingsPresenter extends DocumentSettingsPresenter<FeedSettingsView, FeedDoc> {

    private static final FeedResource FEED_RESOURCE = GWT.create(FeedResource.class);

    @Inject
    public FeedSettingsPresenter(final EventBus eventBus,
                                 final FeedSettingsView view,
                                 final DataTypeUiManager dataTypeUiManager,
                                 final RestFactory restFactory) {
        super(eventBus, view);

        final Rest<List<String>> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    view.getDataEncoding().clear();
                    view.getContextEncoding().clear();

                    if (result != null && result.size() > 0) {
                        for (final String encoding : result) {
                            view.getDataEncoding().addItem(encoding);
                            view.getContextEncoding().addItem(encoding);
                        }
                    }

                    final FeedDoc feed = getEntity();
                    if (feed != null) {
                        view.getDataEncoding().setSelected(ensureEncoding(feed.getEncoding()));
                        view.getContextEncoding().setSelected(ensureEncoding(feed.getContextEncoding()));
                    }
                })
                .call(FEED_RESOURCE)
                .fetchSupportedEncodings();

        view.getFeedStatus().addItems(FeedStatus.values());
        dataTypeUiManager.getTypes(list -> {
            view.getReceivedType().clear();
            if (list != null && !list.isEmpty()) {
                view.getReceivedType().addItems(list);
                final FeedDoc feed = getEntity();
                if (feed != null) {
                    view.getReceivedType().setSelected(feed.getStreamType());
                }
            }
        });

        // Add listeners for dirty events.
        final InputHandler inputHandler = event -> setDirty(true);
        final ValueChangeHandler<Boolean> checkHandler = event -> setDirty(true);
        registerHandler(view.getDescription().addDomHandler(inputHandler, InputEvent.getType()));
        registerHandler(view.getClassification().addDomHandler(inputHandler, InputEvent.getType()));
        registerHandler(view.getReference().addValueChangeHandler(checkHandler));
        registerHandler(view.getDataEncoding().addChangeHandler(event -> {
            final String dataEncoding = ensureEncoding(view.getDataEncoding().getSelected());
            getView().getDataEncoding().setSelected(dataEncoding);

            if (!EqualsUtil.isEquals(dataEncoding, getEntity().getEncoding())) {
                getEntity().setEncoding(dataEncoding);
                setDirty(true);
            }
        }));
        registerHandler(view.getContextEncoding().addChangeHandler(event -> {
            final String contextEncoding = ensureEncoding(view.getContextEncoding().getSelected());
            getView().getContextEncoding().setSelected(contextEncoding);

            if (!EqualsUtil.isEquals(contextEncoding, getEntity().getContextEncoding())) {
                setDirty(true);
                getEntity().setContextEncoding(contextEncoding);
            }
        }));
        registerHandler(view.getFeedStatus().addSelectionHandler(event -> setDirty(true)));
        registerHandler(view.getReceivedType().addChangeHandler(event -> {
            final String streamType = view.getReceivedType().getSelected();
            getView().getReceivedType().setSelected(streamType);

            if (!EqualsUtil.isEquals(streamType, getEntity().getStreamType())) {
                setDirty(true);
                getEntity().setStreamType(streamType);
            }
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final FeedDoc feed) {
        getView().getDescription().setText(feed.getDescription());
        getView().getReference().setValue(feed.isReference());
        getView().getClassification().setText(feed.getClassification());
        getView().getDataEncoding().setSelected(ensureEncoding(feed.getEncoding()));
        getView().getContextEncoding().setSelected(ensureEncoding(feed.getContextEncoding()));
        getView().getReceivedType().setSelected(feed.getStreamType());
        getView().getFeedStatus().setSelectedItem(feed.getStatus());
    }

    @Override
    protected void onWrite(final FeedDoc feed) {
        feed.setDescription(getView().getDescription().getText().trim());
        feed.setReference(getView().getReference().getValue());
        feed.setClassification(getView().getClassification().getText());
        feed.setEncoding(ensureEncoding(getView().getDataEncoding().getSelected()));
        feed.setContextEncoding(ensureEncoding(getView().getContextEncoding().getSelected()));
        feed.setStreamType(getView().getReceivedType().getSelected());
        // Set the process stage.
        feed.setStatus(getView().getFeedStatus().getSelectedItem());
    }

    private String ensureEncoding(final String encoding) {
        if (encoding == null || encoding.trim().length() == 0) {
            return "UTF-8";
        }
        return encoding;
    }

    @Override
    public String getType() {
        return FeedDoc.DOCUMENT_TYPE;
    }

    public interface FeedSettingsView extends View {

        TextArea getDescription();

        TextBox getClassification();

        CustomCheckBox getReference();

        StringListBox getDataEncoding();

        StringListBox getContextEncoding();

        StringListBox getReceivedType();

        ItemListBox<FeedStatus> getFeedStatus();
    }
}
