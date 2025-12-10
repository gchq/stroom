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

package stroom.config.global.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.function.Supplier;

public class GlobalPropertyTabPresenter
        extends ContentTabPresenter<GlobalPropertyTabPresenter.GlobalPropertyTabView>
        implements ManageGlobalPropertyUiHandlers {

    public static final String TAB_TYPE = "Caches";
    public static final String LIST = "LIST";

    private final ManageGlobalPropertyListPresenter listPresenter;
    private final Provider<ManageGlobalPropertyEditPresenter> editProvider;
    private final ButtonView openButton;
    private final ButtonView warningsButton;
    private String currentWarnings;

    @Inject
    public GlobalPropertyTabPresenter(final EventBus eventBus,
                                      final GlobalPropertyTabView view,
                                      final ManageGlobalPropertyListPresenter listPresenter,
                                      final Provider<ManageGlobalPropertyEditPresenter> editProvider,
                                      final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.editProvider = editProvider;
        view.setUiHandlers(this);
        setInSlot(LIST, listPresenter);
        openButton = listPresenter.addButton(SvgPresets.EDIT);

        warningsButton = listPresenter.addButton(SvgPresets.ALERT.title("Show Warnings"));
        warningsButton.setVisible(false);

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() ->
                        QuickFilterTooltipUtil.createTooltip(
                                "Properties Quick Filter",
                                GlobalConfigResource.FIELD_DEFINITIONS,
                                uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    @Override
    public String getLabel() {
        return "Properties";
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.PROPERTIES;
    }

    @Override
    protected void onBind() {
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onOpen();
            }
        }));
        if (openButton != null) {
            registerHandler(openButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    onOpen();
                }
            }));
        }
        registerHandler(warningsButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                showWarnings();
            }
        }));
        registerHandler(listPresenter.addErrorHandler(event -> setErrors(event.getError())));
        super.onBind();
        listPresenter.refresh();
        getView().focusFilter();
    }

    public void setErrors(final String errors) {
        currentWarnings = errors;
        warningsButton.setVisible(currentWarnings != null && !currentWarnings.isEmpty());
    }

    private void showWarnings() {
        if (currentWarnings != null && !currentWarnings.isEmpty()) {
            AlertEvent.fireWarn(this, "Unable to get properties from all nodes:",
                    currentWarnings, null);
        }
    }

    private void enableButtons() {
        final boolean enabled = listPresenter.getSelectedItem() != null;
        openButton.setEnabled(enabled);
    }

    private void onOpen() {
        final ConfigProperty e = listPresenter.getSelectedItem();
        onEdit(e);
    }

    public void onEdit(final ConfigProperty e) {
        if (e != null) {
            if (editProvider != null) {
                final ManageGlobalPropertyEditPresenter editor = editProvider.get();
                editor.setTaskMonitorFactory(this);
                editor.showEntity(e, listPresenter::refresh);
            }
        }
    }

    @Override
    public void changeNameFilter(final String name) {
        if (NullSafe.isNonEmptyString(name)) {
            // This will initiate a timer to refresh the data
            listPresenter.setPartialName(name);
        } else {
            listPresenter.clearFilter();
        }
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.FOCUS_FILTER == action) {
            getView().focusFilter();
            return true;
        }
        return false;
    }

    // --------------------------------------------------------------------------------


    public interface GlobalPropertyTabView extends View, HasUiHandlers<ManageGlobalPropertyUiHandlers> {

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        void focusFilter();
    }
}
