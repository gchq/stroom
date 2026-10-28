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

package stroom.explorer.client.component;

import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// The explorer: the logo and the Main Menu button, the explorer's filter, its toolbar, the tree of
/// documents and, when activities are used, the current activity.
public class Navigation extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Button logo;
    @UiField
    ToolbarButton mainMenuButton;
    @UiField
    QuickFilter nameFilter;
    @UiField
    ToolbarButton add;
    @UiField
    ToolbarButton delete;
    @UiField
    ToolbarToggleButton showAlerts;
    @UiField
    ToolbarButton locate;
    @UiField
    ToolbarButton expandAll;
    @UiField
    ToolbarButton collapseAll;
    @UiField
    ToolbarToggleButton filter;
    @UiField
    ToolbarButton find;
    @UiField
    ExplorerTree explorerTree;
    @UiField
    SimplePanel activityOuter;
    @UiField
    Button activityButton;
    @UiField
    SpinnerSmall spinner;

    private Consumer<String> onFilterChange = text -> {
    };
    private Consumer<Element> onToggleMenu = element -> {
    };
    private Runnable onActivity = () -> {
    };
    private Consumer<ExplorerAction> onAction = action -> {
    };
    private final Map<ExplorerAction, ToolbarButton> buttons = new EnumMap<>(ExplorerAction.class);

    /// Makes the screen, with no content until it is given it.
    public Navigation() {
        initWidget(BINDER.createAndBindUi(this));

        logo.setContent(SvgImageUtil.toSafeHtml(
                SvgImage.LOGO,
                "navigation-logo-image"));
        logo.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event.getNativeEvent())) {
                onToggleMenu.accept(event.getRelativeElement());
            }
        });
        mainMenuButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onToggleMenu.accept(mainMenuButton.getElement());
            }
        });
        nameFilter.setOnChange(text -> onFilterChange.accept(text));
        activityButton.setOnClick(() -> onActivity.run());

        buttons.put(ExplorerAction.NEW, add);
        buttons.put(ExplorerAction.DELETE, delete);
        buttons.put(ExplorerAction.SHOW_ALERTS, showAlerts);
        buttons.put(ExplorerAction.LOCATE, locate);
        buttons.put(ExplorerAction.EXPAND_ALL, expandAll);
        buttons.put(ExplorerAction.COLLAPSE_ALL, collapseAll);
        buttons.put(ExplorerAction.FILTER, filter);
        buttons.put(ExplorerAction.FIND, find);
        buttons.forEach((action, button) -> button.setOnClick(() -> onAction.accept(action)));
    }

    /// @param onAction Told which of the explorer's buttons the user pressed.
    public void setOnAction(final Consumer<ExplorerAction> onAction) {
        this.onAction = Objects.requireNonNull(onAction, "onAction");
    }

    /// @param action  One of the explorer's buttons.
    /// @param enabled Whether the user may press it.
    public void setActionEnabled(final ExplorerAction action, final boolean enabled) {
        buttons.get(action).setEnabled(enabled);
    }

    /// @param action  One of the explorer's buttons.
    /// @param visible Whether to show it.
    public void setActionVisible(final ExplorerAction action, final boolean visible) {
        buttons.get(action).setVisible(visible);
    }

    /// @param action  One of the explorer's toggle buttons (Show Alerts, Filter Document Types).
    /// @param pressed Whether it is pressed (on).
    public void setActionPressed(final ExplorerAction action, final boolean pressed) {
        ((ToolbarToggleButton) buttons.get(action)).setPressed(pressed);
    }

    /// @param action One of the explorer's toggle buttons (Show Alerts, Filter Document Types).
    /// @return Whether it is pressed (on).
    public boolean isActionPressed(final ExplorerAction action) {
        return ((ToolbarToggleButton) buttons.get(action)).isPressed();
    }

    /// @param action One of the explorer's buttons.
    /// @return Its element, e.g. for a menu to open beside.
    public Element getActionElement(final ExplorerAction action) {
        return buttons.get(action).getElement();
    }

    /// @return The tree of documents, for its controller to attach to.
    public ExplorerTree getExplorerTree() {
        return explorerTree;
    }

    /// Shows the tree of documents, hidden until the explorer is first shown.
    public void showExplorerTree() {
        explorerTree.setVisible(true);
    }

    /// @param onFilterChange Given the filter's text when the user changes it.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = Objects.requireNonNull(onFilterChange, "onFilterChange");
    }

    /// @param onToggleMenu Given the element the main menu opens beside, when the user opens or closes
    ///                     it from the Main Menu button or the logo.
    public void setOnToggleMenu(final Consumer<Element> onToggleMenu) {
        this.onToggleMenu = Objects.requireNonNull(onToggleMenu, "onToggleMenu");
    }

    /// @param onActivity Run when the user presses the current activity, to choose another.
    public void setOnActivity(final Runnable onActivity) {
        this.onActivity = Objects.requireNonNull(onActivity, "onActivity");
    }

    /// Sets the help shown by the filter's syntax help button.
    ///
    /// @param popupTextSupplier Supplies the help.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        nameFilter.registerPopupTextProvider(popupTextSupplier);
    }

    /// Shows the current activity below the explorer, or hides it (when activities aren't used).
    ///
    /// @param summary The activity's summary, or null to hide it.
    public void setActivitySummary(final SafeHtml summary) {
        activityOuter.setVisible(summary != null);
        if (summary != null) {
            activityButton.setContent(summary);
        }
    }

    /// Puts the focus on the filter.
    public void focusQuickFilter() {
        nameFilter.focus();
    }

    /// Shows why the server rejected the filter, or clears it when passed null.
    ///
    /// @param filterError Why, or null.
    public void setFilterError(final String filterError) {
        nameFilter.setFilterError(filterError);
    }

    /// @param contextHandler Gives the filter its recent filters.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        nameFilter.setContextHandler(contextHandler);
    }

    /// @return The explorer's spinner, for the progress of its requests.
    public TaskMonitorFactory getTaskListener() {
        return spinner;
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, Navigation> {

    }

    /// The explorer's buttons.
    public enum ExplorerAction {
        NEW,
        DELETE,
        SHOW_ALERTS,
        LOCATE,
        EXPAND_ALL,
        COLLAPSE_ALL,
        FILTER,
        FIND
    }
}
