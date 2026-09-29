/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.view;

import stroom.floormap.client.presenter.FloorMapClusterPresenter.FloorMapClusterView;
import stroom.floormap.client.presenter.FloorMapClusterUiHandlers;
import stroom.item.client.SelectionBox;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.form.client.FormGroup;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;
import javax.inject.Inject;

/// View for the cluster member dialog: a filter bar over the member grid.
///
/// The three controls are equals — the search box and both dropdowns narrow
/// the same list — so they sit on one line rather than the search box being
/// promoted above the filters.
///
/// A dropdown with nothing to choose between is *hidden* rather than
/// disabled (see [#setAreaFilterOptions]): a cluster standing wholly inside
/// one room has nothing to say about areas, and a greyed control would only
/// invite the user to work out why.
public class FloorMapClusterViewImpl
        extends ViewWithUiHandlers<FloorMapClusterUiHandlers>
        implements FloorMapClusterView {

    private final Widget widget;

    @UiField
    QuickFilter quickFilter;
    @UiField
    FormGroup areaFilterGroup;
    @UiField
    SelectionBox<String> areaFilter;
    @UiField
    FormGroup groupFilterGroup;
    @UiField
    SelectionBox<String> groupFilter;
    @UiField
    SimplePanel data;

    /// True while the controls are being repopulated for a new cluster, so the
    /// resulting value changes are not reported as the user filtering. Without it
    /// every showing would run the filter several times over, and — worse — would
    /// do so between clearing the options and setting the new ones, when the
    /// dropdowns momentarily hold values belonging to the previous cluster.
    private boolean populating;

    @Inject
    public FloorMapClusterViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        // The widget's default help text describes the platform's field-qualified
        // quick filter, which this box is not — it says what this one actually
        // does, including the one rule a user could not guess (several words all
        // have to match).
        quickFilter.registerPopupTextProvider(() -> new HtmlBuilder()
                .bold("Search")
                .br()
                .append("Matches anywhere in a member's name, id, type, area or group.")
                .br()
                .append("Every word has to match something, so \"ali bay\" finds Alice "
                        + "in the Loading Bay.")
                .br()
                .toSafeHtml());

        //noinspection unused e
        quickFilter.addValueChangeHandler(e -> fireFilterChange());
        //noinspection unused e
        areaFilter.addValueChangeHandler(e -> fireFilterChange());
        //noinspection unused e
        groupFilter.addValueChangeHandler(e -> fireFilterChange());
    }

    private void fireFilterChange() {
        if (!populating && getUiHandlers() != null) {
            getUiHandlers().onFilterChange();
        }
    }

    @Override
    public void setDataView(final View view) {
        data.setWidget(view.asWidget());
    }

    /// {@inheritDoc}
    @Override
    public void setAreaFilterOptions(final List<String> options) {
        populate(areaFilterGroup, areaFilter, options);
    }

    /// {@inheritDoc}
    @Override
    public void setGroupFilterOptions(final List<String> options) {
        populate(groupFilterGroup, groupFilter, options);
    }

    /// Fills one dropdown and shows it, or hides it when there is nothing to
    /// choose between. The first option is the "any" one, and is selected — a
    /// freshly shown dialog filters nothing.
    private void populate(final FormGroup group,
                          final SelectionBox<String> box,
                          final List<String> options) {
        populating = true;
        try {
            box.clear();
            final boolean offered = options != null && !options.isEmpty();
            if (offered) {
                box.addItems(options);
                //noinspection SequencedCollectionMethodCanBeUsed getFirst() not in GWT
                box.setValue(options.get(0));
            }
            group.setVisible(offered);
        } finally {
            populating = false;
        }
    }

    /// {@inheritDoc}
    @Override
    public String getSearchText() {
        return quickFilter.getText();
    }

    /// {@inheritDoc}
    @Override
    public String getAreaFilter() {
        // A hidden control constrains nothing; its stale value must not survive
        // into a cluster whose members it was never built from.
        return areaFilterGroup.isVisible()
                ? areaFilter.getValue()
                : null;
    }

    /// {@inheritDoc}
    @Override
    public String getGroupFilter() {
        return groupFilterGroup.isVisible()
                ? groupFilter.getValue()
                : null;
    }

    /// {@inheritDoc}
    @Override
    public void clearFilters() {
        populating = true;
        try {
            // setText DOES fire a value change, so the guard is load-bearing here:
            // without it, emptying the box would filter the outgoing cluster's
            // members a moment before the new ones arrive.
            quickFilter.setText("");
            areaFilter.clear();
            groupFilter.clear();
        } finally {
            populating = false;
        }
    }

    /// {@inheritDoc}
    @Override
    public void focusSearch() {
        quickFilter.forceFocus();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, FloorMapClusterViewImpl> {

    }
}
