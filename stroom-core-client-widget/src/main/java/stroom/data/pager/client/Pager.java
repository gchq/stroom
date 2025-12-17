/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.SvgButton;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.cellview.client.AbstractPager;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasRows;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent;

import java.util.HashSet;
import java.util.Set;

public class Pager extends AbstractPager {

    private static Binder binder;
    @UiField(provided = true)
    SvgButton first;
    @UiField(provided = true)
    SvgButton prev;
    @UiField(provided = true)
    SvgButton next;
    @UiField(provided = true)
    SvgButton last;
    @UiField(provided = true)
    RefreshButton refresh;
    @UiField
    Label lblTitle;
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

    private final Set<FocusWidget> focussed = new HashSet<>();

    public Pager() {
        if (binder == null) {
            binder = GWT.create(Binder.class);
        }

        first = SvgButton.create(SvgPresets.FAST_BACKWARD_BLUE);
        prev = SvgButton.create(SvgPresets.STEP_BACKWARD_BLUE);
        next = SvgButton.create(SvgPresets.STEP_FORWARD_BLUE);
        last = SvgButton.create(SvgPresets.FAST_FORWARD_BLUE);
        refresh = new RefreshButton();

        initWidget(binder.createAndBindUi(this));

        // Disable the buttons by default.
        setupButton(first);
        setupButton(prev);
        setupButton(next);
        setupButton(last);
        refresh.setEnabled(false);
    }

    private void setupButton(final SvgButton button) {
        button.setEnabled(false);
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

    @UiHandler("txtFrom")
    void onFocusFrom(final FocusEvent event) {
        focussed.add(txtFrom);
    }

    @UiHandler("txtTo")
    void onFocusTo(final FocusEvent event) {
        focussed.add(txtTo);
    }

    @UiHandler("txtFrom")
    void onBlurFrom(final BlurEvent event) {
        focussed.remove(txtFrom);
    }

    @UiHandler("txtTo")
    void onBlurTo(final BlurEvent event) {
        focussed.remove(txtTo);
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
            return 0;
        }
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
    public void setPage(final int index) {
        super.setPage(index);
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

        final boolean isRowCountExact = display.isRowCountExact();
        final boolean hasPreviousPage = hasPreviousPage();
        final boolean hasNextPage = hasNextPage();

        // If we aren't currently editing from or to values then turn editing off.
        if (focussed.size() == 0) {
            setEditing(false);
        }

        lblFrom.setText(formatter.format(pageStart));
        lblTo.setText(formatter.format(endIndex));
        if (isRowCountExact) {
            lblOf.setText(formatter.format(dataSize));
        } else {
            lblOf.setText("?");
        }

        // Update the buttons.
        first.setEnabled(hasPreviousPage);
        prev.setEnabled(hasPreviousPage);
        next.setEnabled(hasNextPage);
        last.setEnabled(hasNextPage && isRowCountExact);

        refresh.setEnabled(true);
    }

    public RefreshButton getRefreshButton() {
        return refresh;
    }

    public void setTitle(final String title) {
        lblTitle.setText(title);
    }

    public interface Binder extends UiBinder<Widget, Pager> {

    }
}
