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

package stroom.config.global.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.content.client.component.ContentTabController;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;


/// Shows Stroom's configuration properties, filtered as the user asks, and edits them.
public class GlobalPropertyTabController extends ContentTabController {

    public static final String TAB_TYPE = "Properties";

    private final GlobalPropertyTab view;

    private final ManageGlobalPropertyListController manageGlobalPropertyList;
    private final Provider<GlobalPropertyEditController> editProvider;
    private final ButtonView openButton;
    private final ButtonView warningsButton;
    private String currentWarnings;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             Its component.
    /// @param manageGlobalPropertyList         The manage global property list.
    /// @param editProvider                     Makes the edit.
    /// @param uiConfigCache                    The UI's configuration.
    /// @param quickFilterContextHandlerFactory Adds the quick filter's history and Advanced Query.
    @Inject
    public GlobalPropertyTabController(final EventBus eventBus,
                                       final GlobalPropertyTab view,
                                       final ManageGlobalPropertyListController manageGlobalPropertyList,
                                       final Provider<GlobalPropertyEditController> editProvider,
                                       final UiConfigCache uiConfigCache,
                                       final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.manageGlobalPropertyList = own(manageGlobalPropertyList).attach(view.getList());
        this.editProvider = editProvider;
        view.setOnFilterChange(this::changeNameFilter);
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
        view.focusFilter();
    }

    @Override
    public String getLabel() {
        return "Properties";
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.PROPERTIES;
    }

    /// Shows the Show Warnings button when some nodes didn't give their properties.
    ///
    /// @param errors Why, or null or empty for none.
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

    /// Edits a property.
    ///
    /// @param e The property, or null for none.
    public void onEdit(final ConfigProperty e) {
        if (e != null) {
            if (editProvider != null) {
                editProvider.get().show(this, e, manageGlobalPropertyList::refresh);
            }
        }
    }

    /// Shows what matches a filter.
    ///
    /// @param name The filter.
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
            view.focusFilter();
            return true;
        }
        return false;
    }
}
