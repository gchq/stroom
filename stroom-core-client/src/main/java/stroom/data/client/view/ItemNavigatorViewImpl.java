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
    SvgButton firstItemBtn;
    @UiField(provided = true)
    SvgButton previousItemBtn;
    @UiField(provided = true)
    SvgButton nextItemBtn;
    @UiField(provided = true)
    SvgButton lastItemBtn;

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
    }

    private void initButtons() {
        // Char buttons
        final String lowerCaseName = display.getName().toLowerCase();
        firstItemBtn = SvgButton.create(
                SvgPresets.FAST_BACKWARD_BLUE.title("Show first " + lowerCaseName));
        previousItemBtn = SvgButton.create(
                SvgPresets.STEP_BACKWARD_BLUE.title("Previous " + lowerCaseName));
        nextItemBtn = SvgButton.create(
                SvgPresets.STEP_FORWARD_BLUE.title("Next " + lowerCaseName));
        lastItemBtn = SvgButton.create(
                SvgPresets.FAST_FORWARD_BLUE.title("Last " + lowerCaseName));
        refreshBtn = SvgButton.create(
                SvgPresets.REFRESH_BLUE);

        setupButton(firstItemBtn, true, false);
        setupButton(previousItemBtn, true, false);
        setupButton(nextItemBtn, true, false);
        setupButton(lastItemBtn, true, false);
        setupButton(refreshBtn, true, true);
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

            final String lbl = display.getName() + " "
                    + getLongValueForLabel(
                            Optional.of(display.getItemNo()),
                            ZERO_TO_ONE_BASE_INCREMENT)
                    + " of "
                    + getLongValueForLabel(display.getTotalItemsCount().asOptional());

            lblDetail.setText(lbl);

            firstItemBtn.setEnabled(!display.isFirstItem());
            previousItemBtn.setEnabled(!display.isFirstItem());

            nextItemBtn.setEnabled(!display.isLastItem());
            lastItemBtn.setEnabled(!display.isLastItem());

            setControlVisibility(true);
        } else {
            setControlVisibility(false);
        }
    }

    private void setControlVisibility(final boolean isVisible) {
        lblDetail.setVisible(isVisible);
        firstItemBtn.setVisible(isVisible);
        previousItemBtn.setVisible(isVisible);
        nextItemBtn.setVisible(isVisible);
        lastItemBtn.setVisible(isVisible);
    }


    private String getLongValueForLabel(final Optional<Long> value) {
        return getLongValueForLabel(value, 0);
    }

    private String getLongValueForLabel(final Optional<Long> value, final int increment) {
        // Increment allows for switching from zero to one based
        return value
                .map(val -> val + increment)
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

    @UiHandler("lblCharacters")
    public void onClickLabel(final ClickEvent event) {
        labelClickHandler.onClick(event);
    }

    @UiHandler("firstItemBtn")
    void onClickFirstItem(final ClickEvent event) {
        if (display != null) {
            display.firstItem();
        }
    }

    @UiHandler("previousItemBtn")
    void onClickNextItem(final ClickEvent event) {
        if (display != null) {
            display.previousItem();
        }
    }

    @UiHandler("nextItemBtn")
    void onClickPreviousItem(final ClickEvent event) {
        if (display != null) {
            display.nextItem();
        }
    }

    @UiHandler("lastItemBtn")
    void onClickLastItem(final ClickEvent event) {
        if (display != null) {
            display.lastItem();
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

    public void setRefreshing(final boolean refreshing) {
        if (refreshing) {
            refreshBtn.getElement().addClassName("fa-spin");
        } else {
            refreshBtn.getElement().removeClassName("fa-spin");
        }
    }

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
