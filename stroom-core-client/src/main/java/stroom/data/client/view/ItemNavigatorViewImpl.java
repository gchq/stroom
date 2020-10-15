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

package stroom.data.client.view;

import stroom.data.client.presenter.ItemNavigatorPresenter.ItemNavigatorView;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.HasItems;
import stroom.widget.button.client.SvgButton;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ItemNavigatorViewImpl extends ViewImpl implements ItemNavigatorView {

    private static final String UNKNOWN_VALUE = "?";
    private static final int ZERO_TO_ONE_BASE_INCREMENT = 1;
    private static final String NUMBER_FORMAT = "#,###";

    private static Binder binder;

    // Selection controls for the char data in the selected record and/or part
    // Always visible
    @UiField
    Label lblDetail;

    @UiField(provided = true)
    SvgButton firstPageBtn;
    @UiField(provided = true)
    SvgButton previousPageBtn;
    @UiField(provided = true)
    SvgButton nextPageBtn;
    @UiField(provided = true)
    SvgButton lastPageBtn;

    @UiField(provided = true)
    SvgButton refreshBtn;

    private HasItems display;

    private final Set<FocusWidget> focussed = new HashSet<>();

    private ClickHandler labelClickHandler;

    private Widget widget;

    @Inject
    public ItemNavigatorViewImpl(final EventBus eventBus,
                                 final Binder binder) {
        initButtons();
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setDisplay(final HasItems display) {
        this.display = display;
        refreshControls();
    }

    private void initButtons() {

        firstPageBtn = SvgButton.create( SvgPresets.FAST_BACKWARD_BLUE);
        previousPageBtn = SvgButton.create( SvgPresets.STEP_BACKWARD_BLUE);
        nextPageBtn = SvgButton.create( SvgPresets.STEP_FORWARD_BLUE);
        lastPageBtn = SvgButton.create( SvgPresets.FAST_FORWARD_BLUE);
        refreshBtn = SvgButton.create( SvgPresets.REFRESH_BLUE);

        setupButton(firstPageBtn, true, false);
        setupButton(previousPageBtn, true, false);
        setupButton(nextPageBtn, true, false);
        setupButton(lastPageBtn, true, false);
        setupButton(refreshBtn, true, true);
    }

    private void updateButtonTitles() {
        final String lowerCaseName = display.getName().toLowerCase();
        final String text = display.hasMultipleItemsPerPage()
                ? "page of " + lowerCaseName + "s"
                : lowerCaseName;

        firstPageBtn.setTitle("First " + text);
        previousPageBtn.setTitle("Previous " + text);
        nextPageBtn.setTitle("Next " + text);
        lastPageBtn.setTitle("Last " + text);
    }

    private void setupButton(final SvgButton button,
                             final boolean isEnabled,
                             final boolean isVisible) {
        button.setEnabled(isEnabled);
        button.setVisible(isVisible);
        button.getElement()
                .getStyle()
                .setPaddingLeft(1, Style.Unit.PX);
        button.getElement()
                .getStyle()
                .setPaddingRight(1, Style.Unit.PX);
    }

    private void refreshControls() {
        if (display != null && display.areNavigationControlsVisible()) {

            final String offsetFromIncStr = getLongValueForLabel(
                    Optional.of(display.getItemOffsetFrom()),
                    ZERO_TO_ONE_BASE_INCREMENT);

            final String offsetToIncStr = getLongValueForLabel(
                    Optional.of(display.getItemOffsetTo()),
                    ZERO_TO_ONE_BASE_INCREMENT);

            final String lbl = display.getName() + " "
                    + offsetFromIncStr
                    + (display.hasMultipleItemsPerPage() ? " to " + offsetToIncStr : "")
                    + " of "
                    + getLongValueForLabel(display.getTotalItemsCount().asOptional());

            lblDetail.setText(lbl);

            firstPageBtn.setEnabled(!display.isFirstPage());
            previousPageBtn.setEnabled(!display.isFirstPage());

            nextPageBtn.setEnabled(!display.isLastPage());
            lastPageBtn.setEnabled(!display.isLastPage());

            updateButtonTitles();

            setControlVisibility(true);
        } else {
            setControlVisibility(false);
        }
    }

    private void setControlVisibility(final boolean isVisible) {
        lblDetail.setVisible(isVisible);
        firstPageBtn.setVisible(isVisible);
        previousPageBtn.setVisible(isVisible);
        nextPageBtn.setVisible(isVisible);
        lastPageBtn.setVisible(isVisible);
    }


    private String getLongValueForLabel(final Optional<Long> value) {
        return getLongValueForLabel(value, 0);
    }

    private String getLongValueForLabel(final Optional<Long> value, final int increment) {
        // Increment allows for switching from zero to one based
        return value
                .map(val ->
                        val + increment)
                .map(val -> {
                    final NumberFormat formatter = NumberFormat.getFormat(NUMBER_FORMAT);
                    return formatter.format(val);
                })
                .orElse(UNKNOWN_VALUE);
    }

    private String getIntValueForLabel(final Optional<Integer> value) {
        // Increment allows for switching from zero to one based
        return value
                .map(val -> {
                    final NumberFormat formatter = NumberFormat.getFormat(NUMBER_FORMAT);
                    return formatter.format(val);
                })
                .orElse(UNKNOWN_VALUE);
    }

    public void setVisible(final boolean isVisible) {
        setControlVisibility(isVisible);
    }

    // Characters UI handlers
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @UiHandler("lblDetail")
    public void onClickLabel(final ClickEvent event) {
        labelClickHandler.onClick(event);
    }

    @UiHandler("firstPageBtn")
    void onClickFirstPageBtn(final ClickEvent event) {
        if (display != null) {
            display.firstPage();
        }
    }

    @UiHandler("previousPageBtn")
    void onClickNextPageBtn(final ClickEvent event) {
        if (display != null) {
            display.previousPage();
        }
    }

    @UiHandler("nextPageBtn")
    void onClickPreviousPageBtn(final ClickEvent event) {
        if (display != null) {
            display.nextPage();
        }
    }

    @UiHandler("lastPageBtn")
    void onClickLastPageBtn(final ClickEvent event) {
        if (display != null) {
            display.lastPage();
        }
    }

    @UiHandler("refreshBtn")
    void onClickRefresh(final ClickEvent event) {
        if (display != null) {
            display.refresh();
        }
    }

    public void refreshNavigator() {
        refreshControls();
    }

    @Override
    public void setRefreshing(final boolean refreshing) {
        if (refreshing) {
            refreshBtn.getElement().addClassName("fa-spin");
        } else {
            refreshBtn.getElement().removeClassName("fa-spin");
        }
    }

    @Override
    public void setLabelClickHandler(final ClickHandler labelClickHandler) {
        this.labelClickHandler = labelClickHandler;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, ItemNavigatorViewImpl> {
    }
}
