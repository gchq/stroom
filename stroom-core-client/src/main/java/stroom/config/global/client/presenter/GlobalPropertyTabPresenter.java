/*
 * Copyright 2018 Crown Copyright
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
import stroom.config.global.client.component.ManageGlobalPropertyListController;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Supplier;

public class GlobalPropertyTabPresenter
        extends ContentTabPresenter<GlobalPropertyTabPresenter.GlobalPropertyTabView>
        implements ManageGlobalPropertyUiHandlers {

    public static final String TAB_TYPE = "Caches";

    private final ManageGlobalPropertyListController manageGlobalPropertyList;
    private final Provider<ManageGlobalPropertyEditPresenter> editProvider;
    private final ButtonView openButton;
    private final ButtonView warningsButton;
    private String currentWarnings;

    @Inject
    public GlobalPropertyTabPresenter(final EventBus eventBus,
                                      final GlobalPropertyTabView view,
                                      final ManageGlobalPropertyListController manageGlobalPropertyList,
                                      final Provider<ManageGlobalPropertyEditPresenter> editProvider,
                                      final UiConfigCache uiConfigCache,
                                      final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        own(manageGlobalPropertyList);
        this.manageGlobalPropertyList = manageGlobalPropertyList;
        this.editProvider = editProvider;
        view.setUiHandlers(this);
        getView().setListView(manageGlobalPropertyList);
        // A rejected filter comes back as an empty page like any other, so surface the reason on
        // the filter box. See ResultPage.filterError.
        manageGlobalPropertyList.setFilterErrorConsumer(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                GlobalConfigResource.QUICK_FILTER_CONTEXT, this, this));
        manageGlobalPropertyList.getToolbar().setName("Property actions");
        openButton = manageGlobalPropertyList.getToolbar().button(SvgImage.EDIT, "Edit Selected Property",
                this::onOpen);
        openButton.setEnabled(false);

        warningsButton = manageGlobalPropertyList.getToolbar().button(SvgImage.ALERT, "Show Warnings",
                this::showWarnings);
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

        own(manageGlobalPropertyList.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onOpen();
            }
        }));
        manageGlobalPropertyList.setOnErrors(this::setErrors);
        manageGlobalPropertyList.refresh();
        getView().focusFilter();
    }

    @Override
    public String getLabel() {
        return "Properties";
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.PROPERTIES;
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
        final boolean enabled = manageGlobalPropertyList.getSelectedItem() != null;
        openButton.setEnabled(enabled);
    }

    private void onOpen() {
        final ConfigProperty e = manageGlobalPropertyList.getSelectedItem();
        onEdit(e);
    }

    public void onEdit(final ConfigProperty e) {
        if (e != null) {
            if (editProvider != null) {
                final ManageGlobalPropertyEditPresenter editor = makeDialog(editProvider);
                editor.showEntity(e, manageGlobalPropertyList::refresh);
            }
        }
    }

    @Override
    public void changeNameFilter(final String name) {
        if (NullSafe.isNonEmptyString(name)) {
            // This will initiate a timer to refresh the data
            manageGlobalPropertyList.setPartialName(name);
        } else {
            manageGlobalPropertyList.clearFilter();
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

        /// Shows the list of properties.
        ///
        /// @param view Its view.
        void setListView(IsWidget view);

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        void focusFilter();

        /**
         * Show why the server rejected the filter, or clear it when passed null.
         * See {@code ResultPage.filterError}.
         */
        void setFilterError(String filterError);

        void setQuickFilterContextHandler(QuickFilterContextHandler contextHandler);
    }
}
