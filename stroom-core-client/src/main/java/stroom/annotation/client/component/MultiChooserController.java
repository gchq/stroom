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

package stroom.annotation.client.component;

import stroom.annotation.client.component.ChooserController.DataSupplier;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.ColumnSizeConstants;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.popup.client.PopupType;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// Shows a chooser ([Chooser]) of ticked values as a popup below what opens it, for one use, e.g.
/// an annotation's labels: the arrows move, and Space, Enter or a press ticks or unticks the value
/// with the focus. Escape closes the popup.
///
/// ```java
/// final MultiChooserController<AnnotationTag> chooser = multiChooserProvider.get();
/// chooser.setDataSupplier(...);
/// chooser.setAccessibleName("Labels");
/// chooser.setSelectedItems(currentLabels);
/// chooser.show(this, element, "Choose Labels", this::changeAnnotationLabels);
/// ```
///
/// @param <T> The type of the values.
public class MultiChooserController<T> extends Controller {

    private static final TickBoxCell.DefaultAppearance TICK_BOX =
            new TickBoxCell.DefaultAppearance("chooser__tick-box");

    private final Chooser<T> view;
    private final MultiSelectionModel<T> selectionModel = new MultiSelectionModelImpl<>();
    private DataSupplier<T> dataSupplier;
    private Function<T, SafeHtml> displayValueFunction = t -> SafeHtmlUtils.fromString(t.toString());

    /// Made by GIN for one use, with the popup's content.
    ///
    /// @param eventBus      The application's event bus, which the popup is shown on.
    /// @param view          The popup's content.
    /// @param uiConfigCache Gives the quick filter's help link.
    @Inject
    public MultiChooserController(final EventBus eventBus,
                                  final Chooser<T> view,
                                  final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setMultiSelect(true);
        view.setOnFilterChange(this::fetch);

        final DataGrid<T> list = view.getList();
        list.setRowChecked(this::getState);
        final DataGridSelectionEventManager<T> selectionEventManager =
                new DataGridSelectionEventManager<>(list, new MultiSelectionModelImpl<>(), true);
        selectionEventManager.setChooser(this::toggle);
        // The ticks are the rows' own state, not the list's selection
        list.setSelectionModel(null, selectionEventManager);

        // The tick box, as a picture: the option says whether it is ticked
        list.addColumn(new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T value) {
                return TICK_BOX.getPicture(getState(value));
            }
        }, "", ColumnSizeConstants.CHECKBOX_COL);

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

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.setFilterHelp(() -> QuickFilterTooltipUtil.createTooltip(
                        "Choose Item Quick Filter",
                        uiConfig.getHelpUrlQuickFilter()));
            }
        });
    }

    /// Names the list of values for assistive technology, e.g. "Labels" (a list must have one).
    ///
    /// @param name The list's name.
    public void setAccessibleName(final String name) {
        view.setAccessibleName(name);
    }

    /// Sets how a value is shown.
    ///
    /// @param displayValueFunction Gives a value's text, as HTML.
    public void setDisplayValueFunction(final Function<T, SafeHtml> displayValueFunction) {
        this.displayValueFunction = Objects.requireNonNull(displayValueFunction);
    }

    /// Sets where the values come from; they are asked for when the popup is shown, and again
    /// whenever the filter changes.
    ///
    /// @param dataSupplier Gives the values that match a filter.
    public void setDataSupplier(final DataSupplier<T> dataSupplier) {
        this.dataSupplier = dataSupplier;
    }

    /// Ticks the values ticked already, e.g. an annotation's labels; no one is told.
    ///
    /// @param values The values.
    public void setSelectedItems(final List<T> values) {
        selectionModel.setSelectedItems(values);
        view.getList().redraw();
    }

    /// Shows the popup below an element, owned by `opener` until it is closed, with the values
    /// from the data supplier and the focus on them.
    ///
    /// @param opener     What shows it.
    /// @param relativeTo The element it is shown below, e.g. the button that opens it.
    /// @param caption    The popup's caption, e.g. "Choose Labels".
    /// @param onChange   Given the values ticked each time one is ticked or unticked.
    public void show(final HasControllerNode opener,
                     final Element relativeTo,
                     final String caption,
                     final Consumer<List<T>> onChange) {
        Objects.requireNonNull(onChange, "onChange");
        own(selectionModel.addSelectionHandler(e -> onChange.accept(selectionModel.getSelectedItems())));
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.POPUP)
                .caption(caption)
                .popupPosition(ChooserController.below(relativeTo))
                .addAutoHidePartner(relativeTo)
                .onShow(e -> view.focus())
                .fire();
        fetch(null);
    }

    private TickBoxState getState(final T value) {
        return TickBoxState.fromBoolean(value != null && selectionModel.isSelected(value));
    }

    // Ticks or unticks a value, which the chooser's opener is told of
    private void toggle(final T value) {
        selectionModel.setSelected(value, !selectionModel.isSelected(value));
        view.getList().redraw();
    }

    private void fetch(final String filter) {
        if (dataSupplier != null) {
            dataSupplier.onChange(filter, values -> {
                // The values may come after the popup is closed
                if (values != null && !isDisposed()) {
                    view.setValues(values);
                }
            });
        }
    }
}
