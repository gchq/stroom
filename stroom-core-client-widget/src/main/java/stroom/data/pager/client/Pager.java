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

package stroom.data.pager.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.cellview.client.AbstractPager;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasRows;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent;
import stroom.widget.button.client.GlyphButton;
import stroom.widget.button.client.GlyphIcons;

public class Pager extends AbstractPager {
    public interface Binder extends UiBinder<Widget, Pager> {
    }

    private static Binder binder;

    @UiField(provided = true)
    GlyphButton first;
    @UiField(provided = true)
    GlyphButton prev;
    @UiField(provided = true)
    GlyphButton next;
    @UiField(provided = true)
    GlyphButton last;
    @UiField(provided = true)
    GlyphButton refresh;
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
    @UiField
    Label lblToSeparator;
    @UiField
    Label lblOfSeparator;

    private boolean editing;

    public Pager() {
        if (binder == null) {
            binder = GWT.create(Binder.class);
        }

        first = GlyphButton.create("fa fa-fast-backward", GlyphIcons.BLUE, "First", false);
        prev = GlyphButton.create("fa fa-step-backward", GlyphIcons.BLUE, "Backward", false);
        next = GlyphButton.create("fa fa-step-forward", GlyphIcons.BLUE, "Forward", false);
        last = GlyphButton.create("fa fa-fast-forward", GlyphIcons.BLUE, "Last", false);
        refresh = GlyphButton.create("fa fa-refresh", GlyphIcons.BLUE, "Refresh", false);

        initWidget(binder.createAndBindUi(this));

        // Disable the buttons by default.
        setupButton(first);
        setupButton(prev);
        setupButton(next);
        setupButton(last);
        setupButton(refresh);
    }

    private void setupButton(GlyphButton button) {
        button.setEnabled(false);
        button.getElement().getStyle().setPaddingLeft(1, Style.Unit.PX);
        button.getElement().getStyle().setPaddingRight(1, Style.Unit.PX);
    }

    @UiHandler("first")
    void onClickFirst(final ClickEvent event) {
        setEditing(false);
        firstPage();
    }

    @UiHandler("prev")
    void onClickPrev(final ClickEvent event) {
        setEditing(false);
        previousPage();
    }

    @UiHandler("next")
    void onClickNext(final ClickEvent event) {
        setEditing(false);
        nextPage();
    }

    @UiHandler("last")
    void onClickLast(final ClickEvent event) {
        setEditing(false);
        lastPage();
    }

    @UiHandler("refresh")
    void onClickRefresh(final ClickEvent event) {
        setEditing(false);
        RangeChangeEvent.fire(getDisplay(), getDisplay().getVisibleRange());
    }

    @UiHandler("txtFrom")
    void onKeyDownFrom(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            setEditing(false);
        }
    }

    @UiHandler("txtTo")
    void onKeyDownTo(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            setEditing(false);
        }
    }

    @UiHandler("lblFrom")
    public void onClickFrom(final ClickEvent event) {
        setEditing(true);
        txtFrom.setFocus(true);
    }

    @UiHandler("lblTo")
    void onClickTo(final ClickEvent event) {
        setEditing(true);
        txtTo.setFocus(true);
    }

    public void setEditing(final boolean editing) {
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

                lblFrom.setVisible(true);
                lblTo.setVisible(true);
                txtFrom.setVisible(false);
                txtTo.setVisible(false);
            }
        }
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
        }

        return 0;
    }

    @Override
    public void firstPage() {
        super.firstPage();
    }

    @Override
    public int getPage() {
        return super.getPage();
    }

    @Override
    public int getPageCount() {
        return super.getPageCount();
    }

    @Override
    public boolean hasNextPage() {
        return super.hasNextPage();
    }

    @Override
    public boolean hasNextPages(final int pages) {
        return super.hasNextPages(pages);
    }

    @Override
    public boolean hasPage(final int index) {
        return super.hasPage(index);
    }

    @Override
    public boolean hasPreviousPage() {
        return super.hasPreviousPage();
    }

    @Override
    public boolean hasPreviousPages(final int pages) {
        return super.hasPreviousPages(pages);
    }

    @Override
    public void lastPage() {
        super.lastPage();
    }

    @Override
    public void lastPageStart() {
        super.lastPageStart();
    }

    @Override
    public void nextPage() {
        super.nextPage();
    }

    @Override
    public void previousPage() {
        super.previousPage();
    }

    @Override
    public void setPage(final int index) {
        super.setPage(index);
    }

    @Override
    public void setPageSize(final int pageSize) {
        super.setPageSize(pageSize);
    }

    @Override
    public void setPageStart(final int index) {
        super.setPageStart(index);
    }

    /**
     * Let the page know that the table is loading. Call this method to clear
     * all data from the table and hide the current range when new data is being
     * loaded into the table.
     */
    public void startLoading() {
        getDisplay().setRowCount(0, true);
        lblFrom.setText("?");
        lblTo.setText("?");
        lblOf.setText("?");
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
        final boolean exact = display.isRowCountExact();

        lblFrom.setText(formatter.format(pageStart));
        lblTo.setText(formatter.format(endIndex));
        if (exact) {
            lblOf.setText(formatter.format(dataSize));
        } else {
            lblOf.setText("?");
        }

        // Update the prev and first buttons.
        first.setEnabled(hasPreviousPage());
        prev.setEnabled(hasPreviousPage());

        // Update the next and last buttons.
        if (isRangeLimited() || !display.isRowCountExact()) {
            next.setEnabled(hasNextPage());
            last.setEnabled(hasNextPage() && display.isRowCountExact());
        }
        refresh.setEnabled(true);
    }

    /**
     * Check if the next button is enabled. Visible for testing.
     */
    boolean isNextButtonEnabled() {
        return next.isEnabled();
    }

    /**
     * Check if the previous button is enabled. Visible for testing.
     */
    boolean isPreviousButtonEnabled() {
        return prev.isEnabled();
    }

    public void setRefreshing(final boolean refreshing) {
        if (refreshing) {
            refresh.getElement().addClassName("fa-spin");
        } else {
            refresh.getElement().removeClassName("fa-spin");
        }
    }
}
