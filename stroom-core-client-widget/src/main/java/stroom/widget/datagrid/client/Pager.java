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

package stroom.widget.datagrid.client;

import stroom.widget.button.client.RefreshButton;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.AbstractPager;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasRows;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent;

import java.util.HashSet;
import java.util.Set;

public class Pager extends AbstractPager {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String CHOOSE_ROWS = "Choose the rows to show";
    @UiField
    ToolbarButton first;
    @UiField
    ToolbarButton prev;
    @UiField
    ToolbarButton next;
    @UiField
    ToolbarButton last;
    @UiField
    RefreshButton refresh;
    @UiField
    Label lblFrom;
    @UiField
    TextBox txtFrom;
    @UiField
    Label lblTo;
    @UiField
    TextBox txtTo;
    @UiField
    Label lblOf;
    private boolean editing;
    // When false, the Last button stays disabled even for an exact row count. Lets a caller show the
    // exact total ("of N") while forbidding a jump to the last page (e.g. a forward-only cursor source
    // that can't seek to an arbitrary offset). Defaults true — normal offset-addressable pagers are
    // unaffected.
    private boolean lastPageAllowed = true;

    private final Set<FocusWidget> focussed = new HashSet<>();

    public Pager() {
        initWidget(BINDER.createAndBindUi(this));
        first.setOnClick(this::onClickFirst);
        prev.setOnClick(this::onClickPrev);
        next.setOnClick(this::onClickNext);
        last.setOnClick(this::onClickLast);
        refresh.addClickHandler(this::onClickRefresh);
        txtFrom.addKeyDownHandler(this::onKeyDownFrom);
        txtTo.addKeyDownHandler(this::onKeyDownTo);
        txtFrom.addFocusHandler(this::onFocusFrom);
        txtTo.addFocusHandler(this::onFocusTo);
        txtFrom.addBlurHandler(this::onBlurFrom);
        txtTo.addBlurHandler(this::onBlurTo);
        lblFrom.addClickHandler(this::onClickFrom);
        lblTo.addClickHandler(this::onClickTo);

        // The rows shown are a button, the keyboard's way to the boxes that choose them (the mouse
        // clicks either number): Enter or Space shows the boxes, From first, then Tab reaches To
        lblFrom.getElement().setTabIndex(0);
        lblFrom.getElement().setAttribute("role", "button");
        lblFrom.setTitle(CHOOSE_ROWS);
        lblFrom.addDomHandler(this::onKeyDownRows, KeyDownEvent.getType());
        txtFrom.setAriaLabel("First row");
        txtTo.setAriaLabel("Last row");

        // Disable the buttons by default.
        setupButton(first);
        setupButton(prev);
        setupButton(next);
        setupButton(last);
        refresh.setEnabled(false);
    }

    private void setupButton(final ToolbarButton button) {
        button.setEnabled(false);
    }

    private void onClickFirst() {
        setEditing(false);
        firstPage();
    }

    private void onClickPrev() {
        setEditing(false);
        previousPage();
    }

    private void onClickNext() {
        setEditing(false);
        nextPage();
    }

    private void onClickLast() {
        setEditing(false);
        lastPage();
    }

    private void onClickRefresh(final ClickEvent event) {
        setEditing(false);
        RangeChangeEvent.fire(getDisplay(), getDisplay().getVisibleRange());
    }

    private void onKeyDownRows(final KeyDownEvent event) {
        final int key = event.getNativeKeyCode();
        if (key == KeyCodes.KEY_ENTER || key == KeyCodes.KEY_SPACE) {
            event.preventDefault();
            setEditing(true);
            txtFrom.setFocus(true);
        }
    }

    private void onKeyDownFrom(final KeyDownEvent event) {
        onKeyDownBox(event);
    }

    private void onKeyDownTo(final KeyDownEvent event) {
        onKeyDownBox(event);
    }

    // Enter shows the rows typed, Escape the rows as they were; either gives the focus back to the
    // rows shown, as the boxes hide. Escape goes no further, so a dialog the pager is in stays open
    private void onKeyDownBox(final KeyDownEvent event) {
        final int key = event.getNativeKeyCode();
        if (key == KeyCodes.KEY_ENTER || key == KeyCodes.KEY_ESCAPE) {
            event.preventDefault();
            event.stopPropagation();
            if (key == KeyCodes.KEY_ESCAPE) {
                cancelEditing();
            } else {
                setEditing(false);
            }
            lblFrom.getElement().focus();
        }
    }

    // Hides the boxes without showing the rows typed in them
    private void cancelEditing() {
        if (editing) {
            editing = false;
            showLabels();
        }
    }

    private void onFocusFrom(final FocusEvent event) {
        focussed.add(txtFrom);
    }

    private void onFocusTo(final FocusEvent event) {
        focussed.add(txtTo);
    }

    private void onBlurFrom(final BlurEvent event) {
        focussed.remove(txtFrom);
    }

    private void onBlurTo(final BlurEvent event) {
        focussed.remove(txtTo);
    }

    private void onClickFrom(final ClickEvent event) {
        setEditing(true);
        txtFrom.setFocus(true);
    }

    private void onClickTo(final ClickEvent event) {
        setEditing(true);
        txtTo.setFocus(true);
    }

    private void setEditing(final boolean editing) {
        if (this.editing != editing) {
            this.editing = editing;
            if (editing) {
                txtFrom.setText(String.valueOf(getDisplay().getVisibleRange().getStart() + 1));
                txtTo.setText(String.valueOf(
                        getDisplay().getVisibleRange().getStart() + getDisplay().getVisibleRange().getLength()));

                txtFrom.setVisible(true);
                txtTo.setVisible(true);
                lblFrom.setVisible(false);
                lblTo.setVisible(false);
            } else {
                fireMoveEvent();
                showLabels();
            }
        }
    }

    private void showLabels() {
        lblFrom.setVisible(true);
        lblTo.setVisible(true);
        txtFrom.setVisible(false);
        txtTo.setVisible(false);
    }

    private void fireMoveEvent() {
        int from = getLong(txtFrom);
        final long to = getLong(txtTo);

        if (from < 1) {
            from = 1;
        }

        if (to > 0 && to >= from) {
            final int length = (int) (to - from + 1);
            getDisplay().setVisibleRange(from - 1, length);
        } else {
            final Range range = getDisplay().getVisibleRange();
            getDisplay().setVisibleRange(from - 1, range.getLength());
        }
    }

    private int getLong(final TextBox textBox) {
        try {
            return Integer.valueOf(textBox.getText().trim());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void onRangeOrRowCountChanged() {
        final NumberFormat formatter = NumberFormat.getFormat("#,###");
        final HasRows display = getDisplay();
        final Range range = display.getVisibleRange();
        final int pageStart = range.getStart() + 1;
        final int pageSize = range.getLength();
        final int dataSize = display.getRowCount();
        int endIndex = Math.min(dataSize, pageStart + pageSize - 1);
        endIndex = Math.max(pageStart, endIndex);

        final boolean isRowCountExact = display.isRowCountExact();
        final boolean hasPreviousPage = hasPreviousPage();
        final boolean hasNextPage = hasNextPage();

        // If we aren't currently editing from or to values then turn editing off.
        if (focussed.size() == 0) {
            setEditing(false);
        }

        // A list known to be empty shows none of none ("0 to 0 of 0"), not a first row it hasn't got
        final boolean empty = isRowCountExact && dataSize == 0;
        lblFrom.setText(formatter.format(empty
                ? 0
                : pageStart));
        lblTo.setText(formatter.format(empty
                ? 0
                : endIndex));
        if (isRowCountExact) {
            lblOf.setText(formatter.format(dataSize));
        } else {
            lblOf.setText("?");
        }
        // The button's name: the rows shown, as the labels read them ("Rows 1 to 100 of 1,234")
        final String total = isRowCountExact
                ? " of " + lblOf.getText()
                : "";
        lblFrom.getElement().setAttribute("aria-label",
                "Rows " + lblFrom.getText() + " to " + lblTo.getText() + total);
        // An empty list has no rows to choose, so its pager adds no tab stop
        lblFrom.getElement().setTabIndex(empty
                ? -1
                : 0);

        // Update the buttons.
        first.setEnabled(hasPreviousPage);
        prev.setEnabled(hasPreviousPage);
        next.setEnabled(hasNextPage);
        last.setEnabled(hasNextPage && isRowCountExact && lastPageAllowed);

        refresh.setEnabled(true);
    }

    public RefreshButton getRefreshButton() {
        return refresh;
    }

    /**
     * Controls whether the Last button may be enabled. When {@code false} it stays disabled even with an
     * exact row count, so a caller can show the exact total while forbidding a jump to the last page.
     */
    public void setLastPageAllowed(final boolean lastPageAllowed) {
        this.lastPageAllowed = lastPageAllowed;
        final HasRows display = getDisplay();
        last.setEnabled(display != null && display.isRowCountExact() && hasNextPage() && lastPageAllowed);
    }

    public interface Binder extends UiBinder<Widget, Pager> {

    }
}
