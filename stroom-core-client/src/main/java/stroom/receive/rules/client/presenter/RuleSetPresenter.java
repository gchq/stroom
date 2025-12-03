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

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.core.client.HasSave;
import stroom.core.client.HasSaveRegistry;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.receive.rules.shared.ReceiveDataRuleSetResource;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.LazyValue;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.PresenterWidget;

public class RuleSetPresenter extends ContentTabPresenter<LinkTabPanelView>
        implements HasDirtyHandlers, CloseContentEvent.Handler, HasSave, DocumentTabData {

    private static final ReceiveDataRuleSetResource RULES_RESOURCE = GWT.create(ReceiveDataRuleSetResource.class);
    private static final TabData RULES_TAB = new TabDataImpl("Rules");
    private static final TabData FIELDS_TAB = new TabDataImpl("Fields");
    private static final TabData DOCUMENTATION_TAB = new TabDataImpl("Documentation");
    private static final String TAB_LABEL = "Data Receipt Rules";
    private final ButtonView saveButton;
    private final ButtonView warningButton;

    private final RuleSetSettingsPresenter ruleSetSettingsPresenter;
    private final LazyValue<FieldListPresenter> lazyFieldListPresenter;
    private final LazyValue<MarkdownEditPresenter> lazyMarkdownEditPresenter;
    private final RestFactory restFactory;
    private final ButtonPanel toolbar;

    private PresenterWidget<?> currentContent;
    private boolean dirty;
    private String lastLabel;
    private ReceiveDataRules receiveDataRules = null;

    @Inject
    public RuleSetPresenter(final EventBus eventBus,
                            final LinkTabPanelView view,
                            final RuleSetSettingsPresenter ruleSetSettingsPresenter,
                            final Provider<FieldListPresenter> fieldListPresenterProvider,
                            final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                            final RestFactory restFactory,
                            final HasSaveRegistry hasSaveRegistry,
                            final UiConfigCache uiConfigCache) {

        super(eventBus, view);
        this.ruleSetSettingsPresenter = ruleSetSettingsPresenter;
        registerHandler(this.ruleSetSettingsPresenter.addDirtyHandler(event -> {
//            GWT.log("dirty event from ruleSetSettingsPresenter: " + event.isDirty());
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        this.lazyFieldListPresenter = new LazyValue<>(
                fieldListPresenterProvider,
                fieldListPresenter -> {
                    setFieldsOnPresenter(fieldListPresenter);
                    registerHandler(fieldListPresenter.addDirtyHandler(event -> {
                        if (event.isDirty()) {
                            setDirty(true);
                        }
                    }));
                });
        this.lazyMarkdownEditPresenter = new LazyValue<>(
                markdownEditPresenterProvider,
                markdownEditPresenter -> {
                    setDescriptionOnPresenter(markdownEditPresenter);
                    registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
                        if (event.isDirty()) {
                            setDirty(true);
                        }
                    }));
                });
        this.restFactory = restFactory;

        hasSaveRegistry.register(this);
        this.saveButton = SvgButton.create(SvgPresets.SAVE.title("Save all rules"));
        this.saveButton.setEnabled(false);
        this.warningButton = SvgButton.create(SvgPresets.ALERT.title("Show Warnings"));

        toolbar = new ButtonPanel();
        uiConfigCache.get(extendedUiConfig -> {
            initToolbar(toolbar, extendedUiConfig.getReceiptCheckMode());
        }, this);

        addTab(RULES_TAB);
        addTab(FIELDS_TAB);
        addTab(DOCUMENTATION_TAB);
        selectTab(RULES_TAB);

        restFactory
                .create(RULES_RESOURCE)
                .method(ReceiveDataRuleSetResource::fetch)
                .onSuccess(result -> {
                    receiveDataRules = result;
                    // Make sure the rules and lists are in mutable lists so we can mutate the doc
                    receiveDataRules.setRules(NullSafe.mutableList(receiveDataRules.getRules()));
                    receiveDataRules.setFields(NullSafe.mutableList(receiveDataRules.getFields()));

                    ruleSetSettingsPresenter.read(receiveDataRules.asDocRef(), receiveDataRules, false);
                    lazyFieldListPresenter.consumeIfInitialised(this::setFieldsOnPresenter);
                    lazyMarkdownEditPresenter.consumeIfInitialised(this::setDescriptionOnPresenter);
                })
                .onFailure(error -> AlertEvent.fireError(
                        RuleSetPresenter.this,
                        "Unable to load Receive Data Rules", error.getMessage(),
                        null))
                .taskMonitorFactory(this)
                .exec();
    }

    private void initToolbar(final ButtonPanel toolbar, final ReceiptCheckMode receiptCheckMode) {
//        GWT.log("receiptCheckMode: " + receiptCheckMode);
        toolbar.addButton(saveButton);
        registerHandler(saveButton.addClickHandler(event -> save()));

        if (receiptCheckMode != ReceiptCheckMode.RECEIPT_POLICY) {
            toolbar.addButton(warningButton);
            registerHandler(warningButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    showWarnings(receiptCheckMode);
                }
            }));
        }
    }

    private void showWarnings(final ReceiptCheckMode receiptCheckMode) {
        final String msg =
                "Stroom is currently configured with a receiptCheckMode of '" +
                receiptCheckMode.name() + "' so these Data Receipt Rules will have no effect.\n\n" +
                "To use Data Receipt Rules, change the receiptCheckMode property to '" +
                ReceiptCheckMode.RECEIPT_POLICY + "'.";
        AlertEvent.fireWarn(this, msg, null, null);
    }

    private void setDescriptionOnPresenter(final MarkdownEditPresenter markdownEditPresenter) {
        final ReceiveDataRules receiveDataRules = getReceiveDataRules();
        markdownEditPresenter.setText(receiveDataRules.getDescription());
        markdownEditPresenter.setReadOnly(false);
    }

    private void setFieldsOnPresenter(final FieldListPresenter fieldListPresenter) {
        final ReceiveDataRules receiveDataRules = getReceiveDataRules();
        fieldListPresenter.read(receiveDataRules.asDocRef(), receiveDataRules, false);
    }

//    private void setRulesOnPresenter(final RuleSetSettingsPresenter ruleSetSettingsPresenter) {
//        final ReceiveDataRules receiveDataRules = getReceiveDataRules();
//        ruleSetSettingsPresenter.onRead(receiveDataRules.asDocRef(), receiveDataRules, false);
//    }

    private ReceiveDataRules getReceiveDataRules() {
        return receiveDataRules;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(ruleSetSettingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(getView().getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));
        registerHandler(getView().getTabBar().addShowMenuHandler(event ->
                getEventBus().fireEvent(event)));

        registerHandler(getEventBus().addHandler(SaveDocumentEvent.getType(), event -> {
            if (isDirty(event.getTabData())) {
                save();
            }
        }));
    }

    public void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
    }

    public void selectTab(final TabData tab) {
        final TaskMonitor taskMonitor = createTaskMonitor();
        final Task task = new SimpleTask("Selecting tab");
        taskMonitor.onStart(task);
        Scheduler.get().scheduleDeferred(() -> {
            if (tab != null) {
                getContent(tab, content -> {
                    if (content != null) {
                        currentContent = content;

                        // Set the content.
                        getView().getLayerContainer().show((Layer) currentContent);

                        // Update the buttons.
                        if (currentContent instanceof final HasToolbar hasToolbar) {
                            getView().clearToolbar();
                            getView().addToolbar(toolbar);

                            for (final Widget widget : hasToolbar.getToolbars()) {
                                getView().addToolbar(widget);
                            }
                        } else {
                            getView().clearToolbar();
                            getView().addToolbar(toolbar);
                        }

                        // Update the selected tab.
                        getView().getTabBar().selectTab(tab);
                        afterSelectTab(content);
                    }
                });
            }
            taskMonitor.onEnd(task);
        });
    }

    protected void afterSelectTab(final PresenterWidget<?> content) {
    }

    @Override
    public void save() {
        restFactory.create(RULES_RESOURCE)
                .method(resource -> resource.update(receiveDataRules))
                .onSuccess(persistedRules -> {
                    receiveDataRules = persistedRules;
                    setDirty(false);
                })
                .onFailure(error -> AlertEvent.fireError(
                        RuleSetPresenter.this,
                        "Unable to save Receive Data Rules", error.getMessage(),
                        null))
                .taskMonitorFactory(this)
                .exec();
    }

    public boolean isDirty() {
        return dirty;
    }

    void setDirty(final boolean dirty) {
        GWT.log("dirty: " + dirty);
        if (this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);

            if (lastLabel == null || !lastLabel.equals(getLabel())) {
                lastLabel = getLabel();
                RefreshContentTabEvent.fire(this, this);
            }
        }
        ruleSetSettingsPresenter.setDirty(dirty);
        lazyFieldListPresenter.consumeIfInitialised(fieldListPresenter ->
                fieldListPresenter.setDirty(dirty));
        saveButton.setEnabled(dirty);
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET;
    }

    @Override
    public String getLabel() {
        return isDirty()
                ? "* " + TAB_LABEL
                : TAB_LABEL;
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (RULES_TAB.equals(tab)) {
            callback.onReady(ruleSetSettingsPresenter);
        } else if (FIELDS_TAB.equals(tab)) {
            callback.onReady(lazyFieldListPresenter.getValue());
        } else if (DOCUMENTATION_TAB.equals(tab)) {
            callback.onReady(lazyMarkdownEditPresenter.getValue());
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public String getType() {
        return ReceiveDataRules.TYPE;
    }

    @Override
    public void onCloseRequest(final CloseContentEvent event) {
        final DirtyMode dirtyMode = event.getDirtyMode();
        if (dirty && DirtyMode.FORCE != dirtyMode) {
            if (DirtyMode.CONFIRM_DIRTY == dirtyMode) {
                ConfirmEvent.fire(this,
                        "There are unsaved changes. Are you sure you want to close this tab?",
                        result -> {
                            event.getCallback().closeTab(result);
                            if (result) {
                                unbind();
                            }
                        });
            } else if (DirtyMode.SKIP_DIRTY == dirtyMode) {
                // Do nothing
            } else {
                throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
            }
        } else {
            event.getCallback().closeTab(true);
            unbind();
        }
    }

    private boolean isDirty(final TabData tabData) {
        if (tabData instanceof final HasSave hasSave) {
            return hasSave.isDirty();
        } else {
            return false;
        }
    }

    @Override
    public DocRef getDocRef() {
        return NullSafe.get(receiveDataRules, ReceiveDataRules::asDocRef);
    }
}
