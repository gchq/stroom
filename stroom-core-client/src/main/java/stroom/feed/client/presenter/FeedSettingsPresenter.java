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

package stroom.feed.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.app.client.event.DirtyKeyDownHander;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.EntitySettingsPresenter;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.feed.shared.Feed;
import stroom.feed.shared.Feed.FeedStatus;
import stroom.feed.shared.FetchSupportedEncodingsAction;
import stroom.item.client.ItemListBox;
import stroom.item.client.StringListBox;
import stroom.pipeline.shared.SupportedRetentionAge;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.client.presenter.StreamTypeUiManager;
import stroom.streamstore.shared.StreamType;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.SharedString;
import stroom.widget.tickbox.client.view.TickBox;

public class FeedSettingsPresenter extends EntitySettingsPresenter<FeedSettingsPresenter.FeedSettingsView, Feed>
        implements HasRead<Feed>, HasWrite<Feed> {
    public interface FeedSettingsView extends View {
        TextArea getDescription();

        TextBox getClassification();

        TickBox getReference();

        StringListBox getDataEncoding();

        StringListBox getContextEncoding();

        ItemListBox<StreamType> getStreamType();

        ItemListBox<SupportedRetentionAge> getRetentionAge();

        ItemListBox<FeedStatus> getFeedStatus();

        void setReadOnly(boolean readOnly);
    }

    @Inject
    public FeedSettingsPresenter(final EventBus eventBus, final FeedSettingsView view,
                                 final ClientSecurityContext securityContext, final StreamTypeUiManager streamTypeUiManager,
                                 final ClientDispatchAsync dispatcher) {
        super(eventBus, view, securityContext);

        dispatcher.exec(new FetchSupportedEncodingsAction()).onSuccess(result -> {
            view.getDataEncoding().clear();
            view.getContextEncoding().clear();

            if (result != null && result.size() > 0) {
                for (final SharedString sharedString : result) {
                    final String encoding = sharedString.toString();
                    view.getDataEncoding().addItem(encoding);
                    view.getContextEncoding().addItem(encoding);
                }
            }

            final Feed feed = getEntity();
            if (feed != null) {
                view.getDataEncoding().setSelected(ensureEncoding(feed.getEncoding()));
                view.getContextEncoding().setSelected(ensureEncoding(feed.getContextEncoding()));
            }
        });

        view.getRetentionAge().addItems(SupportedRetentionAge.values());
        view.getFeedStatus().addItems(FeedStatus.values());
        view.getStreamType().addItems(streamTypeUiManager.getRawStreamTypeList());

        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };
        final ValueChangeHandler<TickBoxState> checkHandler = event -> setDirty(true);

        registerHandler(view.getReference().addValueChangeHandler(checkHandler));
        registerHandler(view.getDescription().addKeyDownHandler(keyDownHander));
        registerHandler(view.getClassification().addKeyDownHandler(keyDownHander));
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
        registerHandler(view.getRetentionAge().addSelectionHandler(event -> setDirty(true)));
        registerHandler(view.getFeedStatus().addSelectionHandler(event -> setDirty(true)));
        registerHandler(view.getStreamType().addSelectionHandler(event -> setDirty(true)));
    }

    @Override
    protected void onRead(final Feed feed) {
        getView().getDescription().setText(feed.getDescription());
        getView().getReference().setBooleanValue(feed.isReference());
        getView().getClassification().setText(feed.getClassification());
        getView().getDataEncoding().setSelected(ensureEncoding(feed.getEncoding()));
        getView().getContextEncoding().setSelected(ensureEncoding(feed.getContextEncoding()));
        getView().getStreamType().setSelectedItem(feed.getStreamType());
        getView().getRetentionAge().setSelectedItem(SupportedRetentionAge.get(feed.getRetentionDayAge()));
        getView().getFeedStatus().setSelectedItem(feed.getStatus());
    }

    @Override
    protected void onWrite(final Feed feed) {
        feed.setDescription(getView().getDescription().getText().trim());
        feed.setReference(getView().getReference().getBooleanValue());
        feed.setClassification(getView().getClassification().getText());
        feed.setEncoding(ensureEncoding(getView().getDataEncoding().getSelected()));
        feed.setContextEncoding(ensureEncoding(getView().getContextEncoding().getSelected()));
        feed.setRetentionDayAge(getView().getRetentionAge().getSelectedItem().getDays());
        feed.setStreamType(getView().getStreamType().getSelectedItem());
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
        return Feed.ENTITY_TYPE;
    }
}
