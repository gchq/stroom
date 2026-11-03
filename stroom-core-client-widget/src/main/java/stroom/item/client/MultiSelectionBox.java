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

package stroom.item.client;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.table.client.MyCellTable;
import stroom.docref.HasDisplayValue;
import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.Position;
import stroom.widget.popup.client.presenter.PositionUtil;
import stroom.widget.popup.client.view.SimplePopupLayout;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// A drop down box that lets the user tick any number of items from a list.
///
/// The closed box shows the display values of the ticked items, separated by commas.
/// The popup stays open while the user ticks items, and closes when they click
/// elsewhere or press escape.
///
/// @param <T> The type of the items. Items are matched using [Object#equals(Object)].
public class MultiSelectionBox<T> extends Composite implements Focus, HasValueChangeHandlers<List<T>> {

    private static final int FILTER_THRESHOLD = 10;
    private static final String DISPLAY_DELIMITER = ", ";
    private static final TickBoxCell.Appearance TICK_BOX_APPEARANCE = new TickBoxCell.DefaultAppearance();

    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private final List<T> items = new ArrayList<>();
    private final Set<T> selected = new HashSet<>();
    private Function<T, String> displayValueFunction;
    private Function<T, SafeHtml> renderFunction;

    private PopupPanel popup;
    private MyCellTable<T> cellTable;
    private QuickFilter quickFilter;
    private List<T> visibleItems = new ArrayList<>();

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(svgIconBox.addClickHandler(event -> togglePopup()));
            registerHandler(textBox.addClickHandler(event -> togglePopup()));
            registerHandler(textBox.addKeyDownHandler(event -> {
                if (KeyCodes.KEY_ENTER == event.getNativeKeyCode()) {
                    togglePopup();
                }
            }));
        }
    };

    public MultiSelectionBox() {
        textBox = new TextBox();
        textBox.addStyleName("SelectionBox-textBox stroom-control allow-focus pointer");
        textBox.setReadOnly(true);

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName("SelectionBox pointer");
        svgIconBox.setWidget(textBox, SvgImage.DROP_DOWN);

        initWidget(svgIconBox);
    }

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
        hidePopup();
    }

    /// Sets the function used to get the text shown for an item in the closed box.
    /// If not set, [HasDisplayValue#getDisplayValue()] or [Object#toString()] is used.
    ///
    /// @param displayValueFunction The function to get an item's display value.
    public void setDisplayValueFunction(final Function<T, String> displayValueFunction) {
        this.displayValueFunction = displayValueFunction;
    }

    /// Sets the function used to render an item in the popup list.
    /// If not set, the item's display value is shown as plain text.
    ///
    /// @param renderFunction The function to render an item as HTML.
    public void setRenderFunction(final Function<T, SafeHtml> renderFunction) {
        this.renderFunction = renderFunction;
    }

    /// Replaces the items the user can choose from. Any ticked items not in `items` are
    /// unticked.
    ///
    /// @param items The items to choose from, in the order they are shown.
    public void setItems(final List<T> items) {
        this.items.clear();
        NullSafe.forEach(items, item -> {
            if (item != null) {
                this.items.add(item);
            }
        });
        selected.retainAll(this.items);
        updateText();
        refreshList();
    }

    /// @return The ticked items, in the order they appear in the list.
    public List<T> getValue() {
        return items.stream()
                .filter(selected::contains)
                .collect(Collectors.toList());
    }

    /// Ticks the supplied items and unticks all others, without firing a value change event.
    /// Items that are not in the list are ignored.
    ///
    /// @param values The items to tick.
    public void setValue(final List<T> values) {
        selected.clear();
        NullSafe.forEach(values, value -> {
            if (items.contains(value)) {
                selected.add(value);
            }
        });
        updateText();
        refreshList();
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<List<T>> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    private String getDisplayValue(final T item) {
        if (displayValueFunction != null) {
            return displayValueFunction.apply(item);
        } else if (item instanceof final HasDisplayValue hasDisplayValue) {
            return hasDisplayValue.getDisplayValue();
        } else {
            return String.valueOf(item);
        }
    }

    private void updateText() {
        final String text = getValue()
                .stream()
                .map(this::getDisplayValue)
                .collect(Collectors.joining(DISPLAY_DELIMITER));
        textBox.setValue(text);
        textBox.setTitle(text);
    }

    private void toggle(final T item) {
        if (!selected.remove(item)) {
            selected.add(item);
        }
        updateText();
        if (cellTable != null) {
            cellTable.redraw();
        }
        ValueChangeEvent.fire(this, getValue());
    }

    private void togglePopup() {
        if (popup != null) {
            hidePopup();
        } else {
            showPopup();
        }
    }

    private void showPopup() {
        cellTable = new MyCellTable<>(Integer.MAX_VALUE);
        cellTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
        cellTable.addColumn(new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T item) {
                return renderItem(item);
            }
        });
        cellTable.addCellPreviewHandler(this::onCellPreview);

        final ScrollPanel scrollPanel = new ScrollPanel(cellTable);
        scrollPanel.setStyleName("dock-max multiSelectionBox-list");

        final FlowPanel layout = new FlowPanel();
        layout.setStyleName("selectionList multiSelectionBox-popup dock-container-vertical");
        if (items.size() > FILTER_THRESHOLD) {
            quickFilter = new QuickFilter() {
                @Override
                protected void onKeyDown(final KeyDownEvent event) {
                    super.onKeyDown(event);
                    if (event.isDownArrow()) {
                        cellTable.setKeyboardSelectedRow(0, true);
                    } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                        hidePopup();
                    }
                }
            };
            quickFilter.addStyleName("dock-min selectionList-quickFilter");
            quickFilter.addValueChangeHandler(event -> refreshList());
            layout.add(quickFilter);
        }
        layout.add(scrollPanel);

        final SimplePopupLayout simplePopupLayout = new SimplePopupLayout();
        simplePopupLayout.setContent(layout);

        popup = new PopupPanel();
        popup.add(simplePopupLayout);
        popup.setAutoHideEnabled(true);
        popup.setStyleName("SelectionPopup");
        popup.addAutoHidePartner(svgIconBox.getElement());
        popup.addCloseHandler(event -> onPopupClosed());

        refreshList();

        // Show off screen first so the popup can size itself before we position it.
        popup.setPopupPosition(-1000, -1000);
        popup.show();
        final PopupPosition popupPosition = new PopupPosition(new Rect(textBox.getElement()), PopupLocation.BELOW);
        Scheduler.get().scheduleDeferred(() -> {
            if (popup != null) {
                final Position position = PositionUtil.getPosition(
                        4,
                        popupPosition,
                        popup.getOffsetWidth(),
                        popup.getOffsetHeight());
                popup.setPopupPosition((int) position.getLeft(), (int) position.getTop());
                if (quickFilter != null) {
                    quickFilter.forceFocus();
                } else if (!visibleItems.isEmpty()) {
                    cellTable.setKeyboardSelectedRow(0, true);
                }
            }
        });
    }

    private void hidePopup() {
        if (popup != null) {
            popup.hide();
        }
    }

    private void onPopupClosed() {
        popup = null;
        cellTable = null;
        quickFilter = null;
        visibleItems = new ArrayList<>();
        focus();
    }

    private void onCellPreview(final CellPreviewEvent<T> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        final String type = nativeEvent.getType();
        if (BrowserEvents.MOUSEDOWN.equals(type) && MouseUtil.isPrimary(nativeEvent)) {
            toggle(event.getValue());
        } else if (BrowserEvents.KEYDOWN.equals(type)) {
            final int keyCode = nativeEvent.getKeyCode();
            if (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_SPACE) {
                nativeEvent.preventDefault();
                toggle(event.getValue());
            } else if (keyCode == KeyCodes.KEY_ESCAPE) {
                hidePopup();
            } else if (keyCode == KeyCodes.KEY_UP
                       && quickFilter != null
                       && cellTable.getKeyboardSelectedRow() == 0) {
                quickFilter.focus();
            }
        }
    }

    private void refreshList() {
        if (cellTable != null) {
            final String filter = quickFilter != null
                    ? NullSafe.trim(quickFilter.getText()).toLowerCase()
                    : "";
            visibleItems = items.stream()
                    .filter(item -> filter.isEmpty()
                                    || getDisplayValue(item).toLowerCase().contains(filter))
                    .collect(Collectors.toList());
            cellTable.setRowData(0, visibleItems);
            cellTable.setRowCount(visibleItems.size(), true);
        }
    }

    private SafeHtml renderItem(final T item) {
        final SafeHtml label = renderFunction != null
                ? renderFunction.apply(item)
                : SafeHtmlUtils.fromString(getDisplayValue(item));
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"selectionItemCell multiSelectionBox-item\">");
        builder.append(TICK_BOX_APPEARANCE.getHTML(TickBoxState.fromBoolean(selected.contains(item))));
        builder.appendHtmlConstant("<div class=\"selectionItemCell-content\">");
        builder.append(label);
        builder.appendHtmlConstant("</div></div>");
        return builder.toSafeHtml();
    }
}
