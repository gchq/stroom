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
 */

package stroom.policy.client.presenter;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.streamstore.shared.DataReceiptPolicy;
import stroom.streamstore.shared.FetchDataReceiptPolicyAction;
import stroom.streamstore.shared.SaveDataReceiptPolicyAction;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.tab.client.presenter.Layer;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import java.util.ArrayList;
import java.util.List;

public class DataReceiptPolicyPresenter extends ContentTabPresenter<LinkTabPanelView> implements HasDirtyHandlers {
    private static final TabData RULES = new TabDataImpl("Rules");
    private static final TabData FIELDS = new TabDataImpl("Fields");

    private final TabContentProvider<DataReceiptPolicy> tabContentProvider = new TabContentProvider<>();
    private final ClientDispatchAsync dispatcher;

    private DataReceiptPolicy policy;

    private final List<TabData> tabs = new ArrayList<>();
    private ButtonView saveButton;
    private String lastLabel;
    private ButtonPanel leftButtons;
    private PresenterWidget<?> currentContent;

    private boolean dirty;

    @Inject
    public DataReceiptPolicyPresenter(final EventBus eventBus,
                                      final LinkTabPanelView view,
                                      final Provider<DataReceiptPolicySettingsPresenter> settingsPresenterProvider,
                                      final Provider<FieldListPresenter> fieldListPresenterProvider, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.dispatcher = dispatcher;

        saveButton = addButtonLeft(SvgPresets.SAVE);
        saveButton.setEnabled(false);

//        registerHandler(saveButton.addClickHandler(event -> {
//            dispatcher.exec(new SaveDataReceiptPolicyAction(policy)).onSuccess(result -> {
//                policy = result;
//                listPresenter.getSelectionModel().clear();
//                update();
//                setDirty(false);
//            });
//        }));
//        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(RULES);
        tabContentProvider.add(RULES, settingsPresenterProvider);

        addTab(FIELDS);
        tabContentProvider.add(FIELDS, fieldListPresenterProvider);

        selectTab(RULES);

        dispatcher.exec(new FetchDataReceiptPolicyAction()).onSuccess(result -> {
            policy = result;
            update();
        });
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(saveButton.addClickHandler(event -> {
            dispatcher.exec(new SaveDataReceiptPolicyAction(policy)).onSuccess(result -> {
                policy = result;
                update();
                setDirty(false);
            });
        }));
        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));
    }

    private ButtonView addButtonLeft(final SvgPreset preset) {
        if (leftButtons == null) {
            leftButtons = new ButtonPanel();
//            leftButtons.getElement().getStyle().setPaddingLeft(1, Style.Unit.PX);
            addWidgetLeft(leftButtons);
        }

        return leftButtons.add(preset);
    }

    public void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
        tabs.add(tab);
    }

    private void addWidgetLeft(final Widget widget) {
        getView().addWidgetLeft(widget);
    }

    private void selectTab(final TabData tab) {
        TaskStartEvent.fire(DataReceiptPolicyPresenter.this);
        Scheduler.get().scheduleDeferred(() -> {
            if (tab != null) {
                getContent(tab, content -> {
                    if (content != null) {
                        currentContent = content;

                        // Set the content.
                        getView().getLayerContainer().show((Layer) currentContent);

                        // Update the selected tab.
                        getView().getTabBar().selectTab(tab);
                    }
                });
            }

            TaskEndEvent.fire(DataReceiptPolicyPresenter.this);
        });
    }

    private void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.FEED;
    }

    @Override
    public String getLabel() {
        if (isDirty()) {
            return "* " + "Data Receipt";
        }

        return "Data Receipt";
    }

    private boolean isDirty() {
        return dirty;
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);
            saveButton.setEnabled(dirty);

            // Only fire tab refresh if the tab has changed.
            if (lastLabel == null || !lastLabel.equals(getLabel())) {
                lastLabel = getLabel();
                RefreshContentTabEvent.fire(this, this);
            }
        }
    }

    private void update() {
        tabContentProvider.read(policy);
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = policy != null;
        saveButton.setEnabled(loadedPolicy && dirty);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}