/*
 * Copyright 2019 Crown Copyright
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

package stroom.annotation.client;

import stroom.annotation.client.ChooserPresenter.ChooserView;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.datagrid.client.DataGridProfile;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.datagrid.client.DataGridWidgetSelectionEventManager;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class MultiChooserPresenter<T>
        extends MyPresenterWidget<ChooserView>
        implements ChooserUiHandlers {

    private static final TickBoxCell.DefaultAppearance TICK_BOX = new TickBoxCell.DefaultAppearance();

    private final MultiSelectionModel<T> selectionModel = new MultiSelectionModelImpl<>();
    private final DataGridWidget<T> list;
    private DataSupplier<T> dataSupplier;
    private Function<T, SafeHtml> displayValueFunction = t -> SafeHtmlUtils.fromString(t.toString());

    @Inject
    public MultiChooserPresenter(final EventBus eventBus,
                                 final ChooserView view,
                                 final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        view.setUiHandlers(this);

        // A list of values, each ticked or not: the arrows move, and Space, Enter or a press ticks or
        // unticks the value with the focus. As high as its values, up to the chooser's height, then
        // scrolling. Escape goes on to the popup, which closes
        list = new DataGridWidget<>(this, DataGridWidget.MASSIVE_LIST_PAGE_SIZE);
        list.setProfile(DataGridProfile.LISTBOX);
        list.setFitContent(true);
        list.addStyleName("multiChooser");
        list.setRowChecked(this::getState);
        final DataGridWidgetSelectionEventManager<T> selectionEventManager =
                new DataGridWidgetSelectionEventManager<>(list, new MultiSelectionModelImpl<>(), true);
        selectionEventManager.setChooser(this::toggle);
        // The ticks are the rows' own state, not the list's selection
        list.setSelectionModel(null, selectionEventManager);
        view.setBottomWidget(list);

        // The tick box, as a picture: the option says whether it is ticked
        list.addColumn(new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T value) {
                return TICK_BOX.getPicture(getState(value));
            }
        }, "", ColumnSizeConstants.CHECKBOX_COL);

        // Text.
        list.addColumn(new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T value) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                if (value != null) {
                    builder.append(displayValueFunction.apply(value));
                }
                return builder.toSafeHtml();
            }
        }, "", 150);

        // Only deals in lists of strings so no field defs required.
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                getView().registerPopupTextProvider(() ->
                        QuickFilterTooltipUtil.createTooltip(
                                "Choose Item Quick Filter",
                                uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    private TickBoxState getState(final T value) {
        return TickBoxState.fromBoolean(value != null && selectionModel.isSelected(value));
    }

    // Ticks or unticks a value, which the chooser's caller hears of (a MultiSelectEvent)
    private void toggle(final T value) {
        selectionModel.setSelected(value, !selectionModel.isSelected(value));
        list.redraw();
    }

    /// Names the list of values for assistive technology, e.g. "Labels" (a list must have one).
    ///
    /// @param name The list's name.
    public void setAccessibleName(final String name) {
        list.setAccessibleName(name);
    }

    void focus() {
        list.setFocus(true);
    }

    void clearFilter() {
        getView().clearFilter();
    }

    /**
     * Sets the function to provide a display value for value T.
     */
    public void setDisplayValueFunction(final Function<T, SafeHtml> displayValueFunction) {
        this.displayValueFunction = Objects.requireNonNull(displayValueFunction);
    }

    public List<T> getSelectedItems() {
        return selectionModel.getSelectedItems();
    }

    public void setSelectedItems(final List<T> value) {
        selectionModel.setSelectedItems(value);
        list.redraw();
    }

    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return selectionModel.addSelectionHandler(handler);
    }

    @Override
    public void onFilterChange(final String filter) {
        if (dataSupplier != null) {
            dataSupplier.onChange(filter, values -> {
                if (values != null) {
                    list.setRowData(0, values);
                    list.setRowCount(values.size());
                }
            });
        }
    }

    public void setDataSupplier(final DataSupplier<T> dataSupplier) {
        this.dataSupplier = dataSupplier;
    }

    public void refresh() {
        onFilterChange(null);
    }

    public interface DataSupplier<T> {

        void onChange(String filter, Consumer<List<T>> consumer);
    }
}
