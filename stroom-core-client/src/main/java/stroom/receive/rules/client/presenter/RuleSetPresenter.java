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
import stroom.document.client.event.ChangeUiHandlers;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.receive.rules.client.component.FieldListController;
import stroom.receive.rules.client.component.RuleSetSettingsController;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.receive.rules.shared.ReceiveDataRuleSetResource;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.DirtyTracker;
import stroom.widget.presenter.client.Layer;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.LazyValue;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RuleSetPresenter extends ContentTabPresenter<LinkTabPanelView>
        implements CloseContentEvent.Handler, HasSave, DocumentTabData, ChangeUiHandlers {

    private static final ReceiveDataRuleSetResource RULES_RESOURCE = GWT.create(ReceiveDataRuleSetResource.class);
    private static final TabData RULES_TAB = new TabDataImpl("Rules");
    private static final TabData FIELDS_TAB = new TabDataImpl("Fields");
    private static final TabData DOCUMENTATION_TAB = new TabDataImpl("Documentation");
    private static final String TAB_LABEL = "Data Receipt Rules";
    private final ToolbarButton saveButton;

    private final RuleSetSettingsController ruleSetSettings;
    // Shows the Rules tab's component in the tab panel
    private final ControllerLayer ruleSetSettingsLayer;
    private final FieldListController fieldList;
    private final ControllerLayer fieldListLayer;
    private final LazyValue<MarkdownEditController> lazyMarkdownEdit;
    private final RestFactory restFactory;
    private final Toolbar toolbar;
    // The buttons the tab showing added to the toolbar, removed when another tab is shown
    private final List<Widget> tabButtons = new ArrayList<>();
    private final HasSaveRegistry hasSaveRegistry;

    private Layer currentContent;
    // The Documentation tab's controller's layer, made with it
    private ControllerLayer markdownEditLayer;
    private String lastLabel;
    private ReceiveDataRules receiveDataRules = null;
    private boolean dirty;

    @Inject
    public RuleSetPresenter(final EventBus eventBus,
                            final LinkTabPanelView view,
                            final RuleSetSettingsController ruleSetSettings,
                            final Provider<FieldListController> fieldListPresenterProvider,
                            final Provider<MarkdownEditController> markdownEditProvider,
                            final RestFactory restFactory,
                            final HasSaveRegistry hasSaveRegistry,
                            final UiConfigCache uiConfigCache) {

        super(eventBus, view);
        // Its tabs may change the rules: they were never read only here
        provide(Contexts.READ_ONLY, ContextValue.of(false));
        // Its tabs tell it of changes, however deep (markChanged)
        provide(Contexts.DIRTY, ContextValue.<DirtyTracker>of(this::onChange));
        this.ruleSetSettings = own(ruleSetSettings);
        ruleSetSettingsLayer = new ControllerLayer(ruleSetSettings);
        ruleSetSettings.setOnChange(this::onChange);
        fieldList = make(fieldListPresenterProvider);
        fieldListLayer = new ControllerLayer(fieldList);
        ruleSetSettings.setFields(fieldList::getFields);
        this.lazyMarkdownEdit = new LazyValue<>(
                // Made when its tab is first shown, in this screen's scope so it is released with it
                (Supplier<MarkdownEditController>) () -> make(markdownEditProvider),
                this::setDescriptionOnPresenter);
        this.restFactory = restFactory;

        this.hasSaveRegistry = hasSaveRegistry;
        hasSaveRegistry.register(this);
        // The registry is shared, so it would keep this closed screen (and Save All would save it)
        onDispose(() -> hasSaveRegistry.unregister(this));
        toolbar = new Toolbar("Document actions");
        saveButton = toolbar.button(SvgImage.SAVE, "Save Rules", this::save);
        saveButton.setShortcut(Action.ITEM_SAVE);
        saveButton.setEnabled(false);
        uiConfigCache.get(extendedUiConfig -> {
            addWarningButton(extendedUiConfig.getReceiptCheckMode());
        }, this);

        addTab(RULES_TAB);
        addTab(FIELDS_TAB);
        addTab(DOCUMENTATION_TAB);
        selectTab(RULES_TAB);

        restFactory
                .create(RULES_RESOURCE)
                .method(ReceiveDataRuleSetResource::fetch)
                .onSuccess(this::read)
                .onFailure(error -> AlertEvent.fireError(
                        RuleSetPresenter.this,
                        "Unable to load Receive Data Rules", error.getMessage(),
                        null))
                .taskMonitorFactory(this)
                .exec();

        own(getView().getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));
        own(getView().getTabBar().addShowMenuHandler(event ->
                getEventBus().fireEvent(event)));

        listen(SaveDocumentEvent.getType(), event -> {
            if (isDirty(event.getTabData())) {
                save();
            }
        });
    }

    public final boolean isDirty() {
        return dirty;
    }

    private void addWarningButton(final ReceiptCheckMode receiptCheckMode) {
        if (receiptCheckMode != ReceiptCheckMode.RECEIPT_POLICY) {
            toolbar.button(SvgImage.ALERT, "Show Warnings", () -> showWarnings(receiptCheckMode));
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

    private void setDescriptionOnPresenter(final MarkdownEditController markdownEdit) {
        markdownEdit.setText(receiveDataRules.getDescription());
        markdownEdit.setReadOnly(false);
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
                        getView().getLayerContainer().show(currentContent);

                        // Update the buttons: the tab's own buttons join the toolbar (one group, one
                        // tab stop), and its other toolbars follow it
                        for (final Widget widget : tabButtons) {
                            toolbar.remove(widget);
                        }
                        tabButtons.clear();
                        getView().clearToolbar();
                        getView().addToolbar(toolbar);
                        // The Documentation tab's buttons are its controller's
                        final Object toolbarSource = currentContent == markdownEditLayer
                                ? lazyMarkdownEdit.getValue()
                                : currentContent;
                        if (toolbarSource instanceof final HasToolbar hasToolbar) {
                            for (final Widget widget : hasToolbar.getDocumentButtons()) {
                                toolbar.add(widget);
                                tabButtons.add(widget);
                            }
                            for (final Widget widget : hasToolbar.getToolbars()) {
                                getView().addToolbar(widget);
                            }
                        }

                        // Update the selected tab.
                        getView().getTabBar().selectTab(tab);
                    }
                });
            }
            taskMonitor.onEnd(task);
        });
    }

    @Override
    public void save() {
        final ReceiveDataRules updated = write(receiveDataRules);
        restFactory.create(RULES_RESOURCE)
                .method(resource -> resource.update(updated))
                .onSuccess(persistedRules -> {
                    read(persistedRules);
                    onChange();
                })
                .onFailure(error -> AlertEvent.fireError(
                        RuleSetPresenter.this,
                        "Unable to save Receive Data Rules", error.getMessage(),
                        null))
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    public final void onChange() {
        final ReceiveDataRules original = receiveDataRules;
        final ReceiveDataRules updated = write(original);
        final boolean dirty = !Objects.equals(original, updated);
        setDirty(dirty);
    }

    private void read(final ReceiveDataRules rules) {
        this.receiveDataRules = rules;
        ruleSetSettings.read(receiveDataRules);
        fieldList.read(receiveDataRules.asDocRef(), receiveDataRules);
        lazyMarkdownEdit.consumeIfInitialised(this::setDescriptionOnPresenter);
    }

    private ReceiveDataRules write(final ReceiveDataRules rules) {
        ReceiveDataRules updated = rules;
        updated = ruleSetSettings.write(updated);
        updated = fieldList.write(updated);
        if (lazyMarkdownEdit.isInitialised()) {
            updated = updated.copy().description(lazyMarkdownEdit.getValue().getText()).build();
        }
        return updated;
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            updateLabel();
        }
    }

    private void updateLabel() {
        // Only fire tab refresh if the tab has changed.
        if (lastLabel == null || !lastLabel.equals(getLabel())) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
        saveButton.setEnabled(isDirty());
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


    private void getContent(final TabData tab, final Consumer<Layer> callback) {
        if (RULES_TAB.equals(tab)) {
            callback.accept(ruleSetSettingsLayer);
        } else if (FIELDS_TAB.equals(tab)) {
            callback.accept(fieldListLayer);
        } else if (DOCUMENTATION_TAB.equals(tab)) {
            if (markdownEditLayer == null) {
                markdownEditLayer = new ControllerLayer(lazyMarkdownEdit.getValue());
            }
            callback.accept(markdownEditLayer);
        } else {
            callback.accept(null);
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
                            // Closing the tab disposes this
                            event.getCallback().closeTab(result);
                        });
            } else if (DirtyMode.SKIP_DIRTY == dirtyMode) {
                // Do nothing
            } else {
                throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
            }
        } else {
            event.getCallback().closeTab(true);
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
