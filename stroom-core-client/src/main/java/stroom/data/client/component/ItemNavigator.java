/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.client.component;

import stroom.util.shared.HasItems;
import stroom.widget.button.client.LinkButton;
import stroom.widget.button.client.RefreshButton;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Optional;
import java.util.function.Consumer;

/// The data viewer's item navigator: which items are shown (e.g. "Record 1 of 10"), as a link that
/// asks to choose another, and First, Previous, Next, Last and Refresh buttons. It is hidden while
/// there are no items to move between. A button is disabled once pressed, until the items shown
/// next arrive ([#show(HasItems)]). Its markup is `ItemNavigator.ui.xml`;
/// [ItemNavigatorController] fills it.
public class ItemNavigator extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String UNKNOWN_VALUE = "?";
    private static final String NUMBER_FORMAT = "#,###";

    @UiField
    LinkButton lblDetail;
    @UiField
    ToolbarButton firstPageBtn;
    @UiField
    ToolbarButton previousPageBtn;
    @UiField
    ToolbarButton nextPageBtn;
    @UiField
    ToolbarButton lastPageBtn;
    @UiField
    RefreshButton refresh;

    private Consumer<NavigatorAction> onAction;

    /// Makes a navigator with nothing to show.
    public ItemNavigator() {
        initWidget(BINDER.createAndBindUi(this));
        lblDetail.setOnClick(() -> act(NavigatorAction.SELECT));
        firstPageBtn.setOnClick(() -> act(firstPageBtn, NavigatorAction.FIRST));
        previousPageBtn.setOnClick(() -> act(previousPageBtn, NavigatorAction.PREVIOUS));
        nextPageBtn.setOnClick(() -> act(nextPageBtn, NavigatorAction.NEXT));
        lastPageBtn.setOnClick(() -> act(lastPageBtn, NavigatorAction.LAST));
        refresh.setOnClick(() -> act(refresh, NavigatorAction.REFRESH));
    }

    /// @param onAction Given what the user asks for.
    public void setOnAction(final Consumer<NavigatorAction> onAction) {
        this.onAction = onAction;
    }

    /// Shows the items shown and enables the buttons that can move from them, or hides the
    /// navigator if there are none or it isn't wanted.
    ///
    /// @param items The items, or null for none.
    public void show(final HasItems items) {
        final boolean visible = items != null && items.areNavigationControlsVisible();
        setControlVisibility(visible);
        if (visible) {
            final String lbl = items.getName() + " "
                               + format(Optional.of(items.getItemOffsetFrom() + 1))
                               + (items.hasMultipleItemsPerPage()
                    ? " to " + format(Optional.of(items.getItemOffsetTo() + 1))
                    : "")
                               + " of "
                               + format(items.getTotalItemsCount().asOptional());
            lblDetail.setText(lbl);
            // Says what pressing it does, as well as what it shows
            lblDetail.setTitle("Select " + items.getName());
            lblDetail.setAriaLabel("Select " + items.getName() + " (" + lbl + ")");

            firstPageBtn.setEnabled(!items.isFirstPage());
            previousPageBtn.setEnabled(!items.isFirstPage());
            nextPageBtn.setEnabled(!items.isLastPage());
            lastPageBtn.setEnabled(!items.isLastPage());
            refresh.setEnabled(true);

            final String lowerCaseName = items.getName().toLowerCase();
            final String text = items.hasMultipleItemsPerPage()
                    ? "page of " + lowerCaseName + "s"
                    : lowerCaseName;
            firstPageBtn.setTitle("First " + text);
            previousPageBtn.setTitle("Previous " + text);
            nextPageBtn.setTitle("Next " + text);
            lastPageBtn.setTitle("Last " + text);
        }
    }

    /// @param refreshing Whether the items are being fetched, which the refresh button shows.
    public void setRefreshing(final boolean refreshing) {
        refresh.setRefreshing(refreshing);
    }

    private void setControlVisibility(final boolean visible) {
        lblDetail.setVisible(visible);
        firstPageBtn.setVisible(visible);
        previousPageBtn.setVisible(visible);
        nextPageBtn.setVisible(visible);
        lastPageBtn.setVisible(visible);
        refresh.setVisible(visible);
    }

    // Disables the button, so it can't be pressed again while the position changes; the items shown
    // next enable it again if it applies
    private void act(final ToolbarButton button, final NavigatorAction action) {
        button.setEnabled(false);
        act(action);
    }

    private void act(final NavigatorAction action) {
        if (onAction != null) {
            onAction.accept(action);
        }
    }

    private static String format(final Optional<Long> value) {
        return value
                .map(val -> NumberFormat.getFormat(NUMBER_FORMAT).format(val))
                .orElse(UNKNOWN_VALUE);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ItemNavigator> {

    }
}
