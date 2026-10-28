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
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
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
    Toolbar toolbar;
    @UiField
    SimplePanel explorerTreeContainer;
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

    /// Makes the screen, with no content until it is given it.
    public Navigation() {
        initWidget(BINDER.createAndBindUi(this));

        logo.setContent(SvgImageUtil.toSafeHtml(
                SvgImage.LOGO,
                "navigation-logo-image"));
        // A click on the logo opens the main menu, as the Main Menu button beside it does, so for
        // the keyboard and screen readers it would be a second, nameless Main Menu: it is left out
        // of the tab order (-2, as FocusWidget.onAttach turns -1 back into 0) and hidden from them
        logo.getElement().setTabIndex(-2);
        logo.getElement().setAttribute("aria-hidden", "true");
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
        nameFilter.addValueChangeHandler(event -> onFilterChange.accept(nameFilter.getText()));
        activityButton.setOnClick(() -> onActivity.run());
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

    /// @return The explorer's toolbar, for its buttons.
    public Toolbar getToolbar() {
        return toolbar;
    }

    /// Shows the explorer's tree.
    ///
    /// @param widget The tree.
    public void setNavigationWidget(final Widget widget) {
        explorerTreeContainer.setWidget(widget);
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
}
