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

import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// Shows a chooser ([Chooser]) as a popup below what opens it, for one use: a filtered list of
/// values, one of which is chosen with a click, Enter or Space (even the one chosen already).
/// Moving through the list chooses nothing; Escape closes the popup.
///
/// ```java
/// final ChooserController<AnnotationTag> chooser = chooserProvider.get();
/// chooser.setDataSupplier(...);
/// chooser.setAccessibleName("Statuses");
/// chooser.show(this, element, "Choose Status", status -> { ...; chooser.hide(); });
/// ```
///
/// @param <T> The type of the values.
public class ChooserController<T> extends Controller {

    private static final Template TEMPLATE = GWT.create(Template.class);

    private final Chooser<T> view;
    private final MultiSelectionModelImpl<T> selectionModel = new MultiSelectionModelImpl<>();
    private DataSupplier<T> dataSupplier;
    private Function<T, SafeHtml> displayValueFunction = t -> SafeHtmlUtils.fromString(t.toString());
    private Function<T, String> tooltipFunction = null;
    private Consumer<T> onChoose = value -> {
    };

    /// Made by GIN for one use, with the popup's content.
    ///
    /// @param eventBus      The application's event bus, which the popup is shown on.
    /// @param view          The popup's content.
    /// @param uiConfigCache Gives the quick filter's help link.
    @Inject
    public ChooserController(final EventBus eventBus,
                             final Chooser<T> view,
                             final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnFilterChange(this::fetch);

        final DataGrid<T> list = view.getList();
        final DataGridSelectionEventManager<T> selectionEventManager =
                new DataGridSelectionEventManager<>(list, selectionModel, false);
        selectionEventManager.setChooser(value -> {
            selectionModel.setSelected(value);
            onChoose.accept(value);
        });
        list.setSelectionModel(selectionModel, selectionEventManager);
        list.addColumn(new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T value) {
                return item(value);
            }
        }, "", 200);

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.setFilterHelp(() -> QuickFilterTooltipUtil.createTooltip(
                        "Choose Item Quick Filter",
                        uiConfig.getHelpUrlQuickFilter()));
            }
        });
    }

    /// Names the list of values for assistive technology, e.g. "Statuses" (a list must have one).
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

    /// Sets each value's tooltip, e.g. a standard comment's text.
    ///
    /// @param tooltipFunction Gives a value's tooltip, or null or empty for none.
    public void setTooltipFunction(final Function<T, String> tooltipFunction) {
        this.tooltipFunction = tooltipFunction;
    }

    /// Sets where the values come from; they are asked for when the popup is shown, and again
    /// whenever the filter changes.
    ///
    /// @param dataSupplier Gives the values that match a filter.
    public void setDataSupplier(final DataSupplier<T> dataSupplier) {
        this.dataSupplier = dataSupplier;
    }

    /// Shows a value as chosen already, e.g. an annotation's status.
    ///
    /// @param value The value, or null for none.
    public void setSelected(final T value) {
        selectionModel.setSelected(value);
    }

    /// Shows the popup below an element, owned by `opener` until it is closed, with the values
    /// from the data supplier and the focus on them.
    ///
    /// @param opener    What shows it.
    /// @param relativeTo The element it is shown below, e.g. the button that opens it.
    /// @param caption   The popup's caption, e.g. "Choose Status".
    /// @param onChoose  Told each value chosen; it can close the popup with [#hide()].
    public void show(final HasControllerNode opener,
                     final Element relativeTo,
                     final String caption,
                     final Consumer<T> onChoose) {
        this.onChoose = Objects.requireNonNull(onChoose, "onChoose");
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.POPUP)
                .caption(caption)
                .popupPosition(below(relativeTo))
                .addAutoHidePartner(relativeTo)
                .onShow(e -> view.focus())
                .fire();
        fetch(null);
    }

    /// Closes the popup.
    public void hide() {
        ControllerDialog.pressAction(this, DialogAction.OK);
    }

    /// Where a chooser opened from an element goes: below it, its left edge 1px to the left.
    ///
    /// @param element The element it is opened from.
    /// @return The chooser's position.
    static PopupPosition below(final Element element) {
        return new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
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

    private SafeHtml item(final T value) {
        final SafeHtml text = value != null
                ? displayValueFunction.apply(value)
                : SafeHtmlUtils.EMPTY_SAFE_HTML;
        final String tooltip = value != null && tooltipFunction != null
                ? tooltipFunction.apply(value)
                : null;
        return tooltip != null && !tooltip.isEmpty()
                ? TEMPLATE.itemWithTitle(tooltip, text)
                : TEMPLATE.item(text);
    }


    // --------------------------------------------------------------------------------


    // A value's text in the list, with its tooltip if it has one
    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"chooser__item\">{0}</div>")
        SafeHtml item(SafeHtml text);

        @Template("<div class=\"chooser__item\" title=\"{0}\">{1}</div>")
        SafeHtml itemWithTitle(String title, SafeHtml text);
    }


    // --------------------------------------------------------------------------------


    /// Gives a chooser the values that match a filter.
    ///
    /// @param <T> The type of the values.
    public interface DataSupplier<T> {

        /// Asks for the values that match a filter.
        ///
        /// @param filter   The filter's text, or null for none.
        /// @param consumer Given the values, once they are known.
        void onChange(String filter, Consumer<List<T>> consumer);
    }
}
