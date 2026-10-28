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

package stroom.activity.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// The content of the activity chooser: a filter above the user's activities.
public class ManageActivity extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel list;

    private Supplier<SafeHtml> tooltipContentSupplier = () -> null;
    private Consumer<String> onFilterChange = filter -> {
    };

    /// Makes empty content.
    public ManageActivity() {
        initWidget(BINDER.createAndBindUi(this));
        nameFilter.addValueChangeHandler(this::onFilterChange);
        nameFilter.registerPopupTextProvider(() -> tooltipContentSupplier.get());
    }

    /// Puts the focus on the filter.
    public void focus() {
        nameFilter.forceFocus();
    }

    /// @param list The activities, below the filter.
    public void setList(final IsWidget list) {
        this.list.setWidget(list);
    }

    /// @param tooltipContentSupplier Gives the filter's help, when it is asked for.
    public void setTooltipContentSupplier(final Supplier<SafeHtml> tooltipContentSupplier) {
        this.tooltipContentSupplier = tooltipContentSupplier;
    }

    /// Shows why the server rejected the filter. See `ResultPage.filterError`.
    ///
    /// @param filterError Why, or `null` to clear it.
    public void setFilterError(final String filterError) {
        nameFilter.setFilterError(filterError);
    }

    /// @param contextHandler Gives the filter's history and fields.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        nameFilter.setContextHandler(contextHandler);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = onFilterChange;
    }

    private void onFilterChange(final ValueChangeEvent<String> event) {
        onFilterChange.accept(nameFilter.getText());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ManageActivity> {

    }
}
