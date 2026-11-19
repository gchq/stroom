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

package stroom.receive.rules.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.component.DocumentationTabController;
import stroom.document.client.editor.LinkTabPanel;
import stroom.document.client.editor.TabbedController;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.receive.rules.shared.ReceiveDataRuleSetResource;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.DirtyTracker;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The data receipt rules' tabs (Rules, Fields and Documentation), with Save Rules and, when
/// Stroom isn't checking receipt against the rules, a button that warns of it. It fetches the
/// rules itself, and they may always be changed; it tells its owner when they become dirty or
/// clean ([#setOnDirtyChange(Consumer)]).
public class RuleSetEditor extends TabbedController<ReceiveDataRules> {

    private static final ReceiveDataRuleSetResource RULES_RESOURCE = GWT.create(ReceiveDataRuleSetResource.class);

    private static final TabData RULES_TAB = new TabDataImpl("Rules");
    private static final TabData FIELDS_TAB = new TabDataImpl("Fields");
    private static final TabData DOCUMENTATION_TAB = new TabDataImpl("Documentation");

    private final RestFactory restFactory;
    private final FieldListController fieldList;
    private final ToolbarButton saveButton;
    private boolean dirty;
    private Consumer<Boolean> onDirtyChange = dirty -> {
    };

    /// Makes the tabs, and fetches the rules.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 Its component.
    /// @param ruleSetSettings      The rule set settings.
    /// @param fieldList            The field list.
    /// @param documentationTabProvider Makes the Documentation tab's editor.
    /// @param restFactory          Makes REST requests.
    /// @param uiConfigCache        The UI's configuration.
    @Inject
    public RuleSetEditor(final EventBus eventBus,
                         final LinkTabPanel view,
                         final RuleSetSettingsController ruleSetSettings,
                         final FieldListController fieldList,
                         final Provider<DocumentationTabController> documentationTabProvider,
                         final RestFactory restFactory,
                         final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        // Its tabs may change the rules: they were never read only here
        provide(Contexts.READ_ONLY, ContextValue.of(false));
        // Its tabs tell it of changes, however deep (markChanged)
        provide(Contexts.DIRTY, ContextValue.<DirtyTracker>of(this::onChange));

        own(ruleSetSettings);
        this.fieldList = own(fieldList);
        ruleSetSettings.setOnChange(this::onChange);
        ruleSetSettings.setFields(fieldList::getFields);

        saveButton = getToolbar().button(SvgImage.SAVE, "Save Rules", this::save);
        saveButton.setShortcut(Action.ITEM_SAVE);
        saveButton.setEnabled(false);
        uiConfigCache.get(guard(extendedUiConfig -> addWarningButton(extendedUiConfig.getReceiptCheckMode())),
                taskMonitor());

        addOwnedTab(RULES_TAB,
                ruleSetSettings,
                (settings, docRef, rules) -> settings.read(rules),
                RuleSetSettingsController::write);
        addOwnedTab(FIELDS_TAB, fieldList, FieldListController::read, FieldListController::write);
        addTab(DOCUMENTATION_TAB,
                () -> {
                    final DocumentationTabController documentation = documentationTabProvider.get();
                    documentation.followReadOnly();
                    return documentation;
                },
                (documentation, docRef, rules) -> documentation.setText(rules.getDescription()),
                (documentation, rules) -> rules.copy().description(documentation.getText()).build());
        setDefaultTab(RULES_TAB);
        // A tab's menu (e.g. to close others) is the app's
        own(getTabView().getTabBar().addShowMenuHandler(this::fireEvent));

        restFactory
                .create(RULES_RESOURCE)
                .method(ReceiveDataRuleSetResource::fetch)
                .onSuccess(this::read)
                .onFailure(error -> AlertEvent.fireError(
                        this,
                        "Unable to load Receive Data Rules", error.getMessage(),
                        null))
                .owner(this)
                .exec();
    }

    /// @return Whether the rules have changes that aren't saved.
    public boolean isDirty() {
        return dirty;
    }

    /// @param onDirtyChange What to run when the rules become dirty or clean, e.g. to mark the
    ///                      tab's label.
    public void setOnDirtyChange(final Consumer<Boolean> onDirtyChange) {
        this.onDirtyChange = Objects.requireNonNull(onDirtyChange, "onDirtyChange");
    }

    /// Saves the rules, and reads them back.
    public void save() {
        final ReceiveDataRules updated = write();
        if (updated != null) {
            restFactory.create(RULES_RESOURCE)
                    .method(resource -> resource.update(updated))
                    .onSuccess(persistedRules -> {
                        read(persistedRules);
                        onChange();
                    })
                    .onFailure(error -> AlertEvent.fireError(
                            this,
                            "Unable to save Receive Data Rules", error.getMessage(),
                            null))
                    .owner(this)
                    .exec();
        }
    }

    private void read(final ReceiveDataRules rules) {
        read(rules.asDocRef(), rules);
    }

    @Override
    protected void beforeRead(final DocRef docRef, final ReceiveDataRules rules) {
        // The Rules tab needs the fields (to add a rule) before the Fields tab is first shown
        fieldList.read(docRef, rules);
    }

    private void onChange() {
        if (getDocument() != null) {
            setDirty(!Objects.equals(getDocument(), write()));
        }
    }

    private void setDirty(final boolean dirty) {
        saveButton.setEnabled(dirty);
        if (this.dirty != dirty) {
            this.dirty = dirty;
            onDirtyChange.accept(dirty);
        }
    }


    private void addWarningButton(final ReceiptCheckMode receiptCheckMode) {
        if (receiptCheckMode != ReceiptCheckMode.RECEIPT_POLICY) {
            getToolbar().button(SvgImage.ALERT, "Show Warnings", () -> showWarnings(receiptCheckMode));
        }
    }

    private void showWarnings(final ReceiptCheckMode receiptCheckMode) {
        final String msg = "Stroom is currently configured with a receiptCheckMode of '"
                           + receiptCheckMode.name() + "' so these Data Receipt Rules will have no effect.\n\n"
                           + "To use Data Receipt Rules, change the receiptCheckMode property to '"
                           + ReceiptCheckMode.RECEIPT_POLICY + "'.";
        AlertEvent.fireWarn(this, msg, null, null);
    }
}
