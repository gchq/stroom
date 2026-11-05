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

package stroom.query.client.view;

import stroom.query.client.presenter.FieldValuePickerPresenter.FieldValuePickerView;
import stroom.query.shared.FieldValueCount;
import stroom.query.shared.FieldValues;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class FieldValuePickerViewImpl extends ViewImpl implements FieldValuePickerView {

    private static final NumberFormat PERCENT_FORMAT = NumberFormat.getFormat("0.0");
    private static final NumberFormat COUNT_FORMAT = NumberFormat.getFormat("#,##0");
    private static final String COMMA = ",";
    // The size of the corner the browser drags to resize the picker
    private static final int RESIZE_GRIP_SIZE = 16;
    // Matches the padding of .fieldValuePicker in TermEditor.css
    private static final int PANEL_PADDING = 5;

    private final FlowPanel panel;
    private final QuickFilter filterBox;
    private final Label scopeLabel;
    private final SpinnerSmall spinner;
    private final FlowPanel valuesPanel;
    // The tick box of each value listed, by term value, when several values can be picked
    private final Map<String, CustomCheckBox> checkBoxes = new HashMap<>();

    private Consumer<String> filterHandler;
    private Consumer<String> pickHandler;
    private Runnable closeHandler;
    private String firstValue;

    @Inject
    public FieldValuePickerViewImpl() {
        filterBox = new QuickFilter() {
            @Override
            protected void onKeyDown(final KeyDownEvent event) {
                final int keyCode = event.getNativeKeyCode();
                if (keyCode == KeyCodes.KEY_ESCAPE && NullSafe.isEmptyString(getText())) {
                    // Nothing to clear, so close the picker
                    if (closeHandler != null) {
                        closeHandler.run();
                    }
                } else if (keyCode == KeyCodes.KEY_ENTER && firstValue != null && pickHandler != null) {
                    pickHandler.accept(firstValue);
                } else {
                    if (keyCode == KeyCodes.KEY_ESCAPE) {
                        // Only clear the filter, rather than also closing the picker
                        event.stopPropagation();
                        event.preventDefault();
                    }
                    super.onKeyDown(event);
                }
            }
        };
        filterBox.addStyleName("fieldValuePicker-filter");
        filterBox.addValueChangeHandler(event -> {
            if (filterHandler != null) {
                filterHandler.accept(event.getValue());
            }
        });
        filterBox.getElement().getElementsByTagName("input").getItem(0)
                .setAttribute("placeholder", "Filter values");
        filterBox.registerPopupTextProvider(() -> HtmlBuilder.builder()
                .bold("Filter Values")
                .br()
                .append("Lists the most common values that contain the text entered.")
                .toSafeHtml());

        scopeLabel = new Label("", false);
        scopeLabel.setStyleName("fieldValuePicker-scope");
        spinner = new SpinnerSmall();
        spinner.addStyleName("fieldValuePicker-spinner");
        final FlowPanel header = new FlowPanel();
        header.setStyleName("fieldValuePicker-header");
        header.add(scopeLabel);
        header.add(spinner);

        valuesPanel = new FlowPanel();
        valuesPanel.setStyleName("fieldValuePicker-values");

        panel = new FlowPanel();
        panel.setStyleName("fieldValuePicker");
        panel.add(filterBox);
        panel.add(header);
        panel.add(valuesPanel);
        panel.addDomHandler(this::onPanelMouseDown, MouseDownEvent.getType());
    }

    // The picker can be resized by dragging its bottom right corner. Its height is capped so that a long list
    // scrolls, but the browser won't resize past the cap, so the cap is lifted once a resize starts.
    private void onPanelMouseDown(final MouseDownEvent event) {
        final Element element = panel.getElement();
        final boolean onResizeGrip = event.getRelativeX(element) >= element.getClientWidth() - RESIZE_GRIP_SIZE
                                     && event.getRelativeY(element) >= element.getClientHeight() - RESIZE_GRIP_SIZE;
        if (onResizeGrip) {
            final Style style = element.getStyle();
            style.setHeight(element.getClientHeight() - (2 * PANEL_PADDING), Unit.PX);
            style.setProperty("maxHeight", "none");
        }
    }

    @Override
    public void init(final boolean filterable,
                     final Consumer<String> filterHandler,
                     final Consumer<String> pickHandler,
                     final Runnable closeHandler) {
        this.filterHandler = filterHandler;
        this.pickHandler = pickHandler;
        this.closeHandler = closeHandler;
        filterBox.setText("", false);
        filterBox.setVisible(filterable);
        scopeLabel.setText("");
        scopeLabel.setTitle("");
        clearValues();
    }

    @Override
    public void setValues(final FieldValues fieldValues,
                          final String scope,
                          final boolean multiSelect,
                          final Set<String> termValues) {
        clearValues();
        final String scopeText = scope + " - " + COUNT_FORMAT.format(fieldValues.getTotalCount()) + " records";
        scopeLabel.setText(scopeText);
        // The scope, e.g. the query's time conditions, can be too long for the label
        scopeLabel.setTitle(scopeText);

        for (final FieldValueCount valueCount : fieldValues.getValues()) {
            // The values of a term that holds several are separated by commas, so a value containing one can't
            // be one of them
            final boolean pickable = !multiSelect || !valueCount.getValue().contains(COMMA);
            if (pickable && firstValue == null) {
                firstValue = getTermValue(valueCount);
            }
            valuesPanel.add(createRow(valueCount, fieldValues.getTotalCount(), pickable, multiSelect));
        }
        setSelectedValues(termValues);
    }

    @Override
    public void setSelectedValues(final Set<String> termValues) {
        checkBoxes.forEach((termValue, checkBox) -> checkBox.setValue(termValues.contains(termValue)));
    }

    @Override
    public void setMessage(final String message) {
        clearValues();
        final Label label = new Label(message);
        label.setStyleName("fieldValuePicker-message");
        valuesPanel.add(label);
    }

    @Override
    public TaskMonitorFactory getTaskMonitorFactory() {
        return spinner;
    }

    @Override
    public void focus() {
        if (filterBox.isVisible()) {
            filterBox.forceFocus();
        }
    }

    @Override
    public Widget asWidget() {
        return panel;
    }

    private void clearValues() {
        valuesPanel.clear();
        checkBoxes.clear();
        firstValue = null;
    }

    private Widget createRow(final FieldValueCount valueCount,
                             final long totalCount,
                             final boolean pickable,
                             final boolean multiSelect) {
        final double percent = totalCount > 0
                ? Math.min(100, valueCount.getCount() * 100D / totalCount)
                : 0;

        final Label valueLabel = new Label(valueCount.getValue(), false);
        valueLabel.setStyleName("fieldValuePicker-value");
        valueLabel.setTitle(pickable
                ? valueCount.getValue()
                : valueCount.getValue() + " - values containing a comma can't be used with 'in'");

        final FlowPanel barFill = new FlowPanel();
        barFill.setStyleName("fieldValuePicker-barFill");
        barFill.getElement().getStyle().setWidth(percent, Unit.PCT);
        final FlowPanel bar = new FlowPanel();
        bar.setStyleName("fieldValuePicker-bar");
        bar.add(barFill);

        final Label percentLabel = new Label(PERCENT_FORMAT.format(percent) + "%", false);
        percentLabel.setStyleName("fieldValuePicker-percent");
        final String recordsTitle = COUNT_FORMAT.format(valueCount.getCount())
                                    + " out of "
                                    + COUNT_FORMAT.format(totalCount)
                                    + " records have this value";
        percentLabel.setTitle(recordsTitle);
        bar.setTitle(recordsTitle);

        final FlowPanel row = new FlowPanel();
        row.setStyleName("fieldValuePicker-row");
        if (multiSelect) {
            // Shows whether the term holds the value. Clicking it, or anywhere else on the row, adds or removes
            // the value, and the tick follows the term.
            final CustomCheckBox checkBox = new CustomCheckBox();
            checkBox.addStyleName("fieldValuePicker-check");
            checkBox.setTabIndex(-1);
            checkBox.setEnabled(pickable);
            row.add(checkBox);
            if (pickable) {
                checkBoxes.put(getTermValue(valueCount), checkBox);
            }
        }
        row.add(valueLabel);
        row.add(bar);
        row.add(percentLabel);
        if (pickable) {
            row.addDomHandler(event -> {
                if (pickHandler != null) {
                    pickHandler.accept(getTermValue(valueCount));
                }
            }, ClickEvent.getType());
        } else {
            row.addStyleName("fieldValuePicker-row--disabled");
        }
        return row;
    }

    // The data source gives the value as a term must hold it to match, e.g. quoted, if that differs from the value
    private static String getTermValue(final FieldValueCount valueCount) {
        return valueCount.getTermValue() != null
                ? valueCount.getTermValue()
                : valueCount.getValue();
    }
}
