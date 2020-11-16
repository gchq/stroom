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

import stroom.data.client.presenter.CharacterNavigatorPresenter.CharacterNavigatorView;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.HasCharacterData;
import stroom.widget.button.client.SvgButton;
import stroom.widget.progress.client.presenter.ProgressPresenter.ProgressView;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class CharacterNavigatorViewImpl extends ViewImpl implements CharacterNavigatorView {

    private static final String LINES_TITLE = "Lines";
    private static final String CHARACTERS_TITLE = "Characters";
    private static final String UNKNOWN_VALUE = "?";
    private static final int ZERO_TO_ONE_BASE_INCREMENT = 1;
    private static final String NUMBER_FORMAT = "#,###";

    private static Binder binder;
//    private ProgressPresenter progressPresenter;

    // Selection controls for the char data in the selected record and/or part
    // Always visible
    @UiField
    SimplePanel progressBarPanel;
    @UiField
    Label lblLines;
    @UiField
    Label lblCharacters;
    @UiField(provided = true)
    SvgButton showHeadCharactersBtn;
    @UiField(provided = true)
    SvgButton advanceCharactersForwardBtn;
    @UiField(provided = true)
    SvgButton advanceCharactersBackwardBtn;

    @UiField(provided = true)
    SvgButton refreshBtn;

    private HasCharacterData display;

    private final Set<FocusWidget> focussed = new HashSet<>();

    private ClickHandler labelClickHandler;

    private Widget widget;

    @Inject
    public CharacterNavigatorViewImpl(final EventBus eventBus,
                                      final Binder binder) {
        initButtons();
        widget = binder.createAndBindUi(this);
    }

    private void initButtons() {
        // Char buttons
        showHeadCharactersBtn = SvgButton.create(
                SvgPresets.FAST_BACKWARD_BLUE.title("Show Beginning"));
        advanceCharactersBackwardBtn = SvgButton.create(
                SvgPresets.STEP_BACKWARD_BLUE.title("Advance Range Backwards"));
        advanceCharactersForwardBtn = SvgButton.create(
                SvgPresets.STEP_FORWARD_BLUE.title("Advance Range Forwards"));

        setupButton(showHeadCharactersBtn, true, false);
        setupButton(advanceCharactersBackwardBtn, true, false);
        setupButton(advanceCharactersForwardBtn, true, false);

        refreshBtn = SvgButton.create(SvgPresets.REFRESH_BLUE);

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

    private void refreshCharacterControls() {
        if (display != null
                && display.areNavigationControlsVisible()
                && display.getCharFrom().isPresent()
                && display.getCharTo().isPresent()) {

            String linesLbl = "";
            if (display.getLineFrom().isPresent() && display.getLineTo().isPresent()) {
                linesLbl += LINES_TITLE
                        + " "
                        + getIntValueForLabel(display.getLineFrom())
                        + " to "
                        + getIntValueForLabel(display.getLineTo());
            }
            lblLines.setText(linesLbl);

            final String charactersLbl = CHARACTERS_TITLE
                    + " "
                    + getLongValueForLabel(display.getCharFrom(), ZERO_TO_ONE_BASE_INCREMENT)
                    + " to "
                    + getLongValueForLabel(display.getCharTo(), ZERO_TO_ONE_BASE_INCREMENT)
                    + " of "
                    + getLongValueForLabel(display.getTotalChars());

            lblCharacters.setText(charactersLbl);

            final long charFrom = display.getCharFrom().get();
            final long charTo = display.getCharTo().get();

            showHeadCharactersBtn.setEnabled(charFrom > 0);
            advanceCharactersBackwardBtn.setEnabled(charFrom > 0);

            advanceCharactersForwardBtn.setEnabled(
                    charTo < display.getTotalChars().map(total -> total - 1).orElse(Long.MAX_VALUE));

            setCharactersControlVisibility(true);
        } else {
            setCharactersControlVisibility(false);
        }
    }

    private void setCharactersControlVisibility(final boolean isVisible) {
        lblCharacters.setVisible(isVisible);
        showHeadCharactersBtn.setVisible(isVisible);
        advanceCharactersBackwardBtn.setVisible(isVisible);
        advanceCharactersForwardBtn.setVisible(isVisible);
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
        setCharactersControlVisibility(isVisible);
    }

    public void setDisplay(final HasCharacterData display) {
        this.display = display;
    }

//    @Override
//    public void setProgressPresenter(final ProgressPresenter progressPresenter) {
//        this.progressPresenter = progressPresenter;
//    }

    @Override
    public void setProgressView(final ProgressView progressView) {
        this.progressBarPanel.setWidget(progressView.asWidget());
    }

    // Characters UI handlers
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @UiHandler("lblLines")
    public void onClickLinesLabel(final ClickEvent event) {
        labelClickHandler.onClick(event);
    }

    @UiHandler("lblCharacters")
    public void onClickCharactersLabel(final ClickEvent event) {
        labelClickHandler.onClick(event);
    }

    @UiHandler("showHeadCharactersBtn")
    void onClickShowHeadCharacters(final ClickEvent event) {
        if (display != null) {
            display.showHeadCharacters();
        }
    }

    @UiHandler("advanceCharactersBackwardBtn")
    void onClickNextAdvanceCharsBackwards(final ClickEvent event) {
        if (display != null) {
            display.advanceCharactersBackwards();
        }
    }

    @UiHandler("advanceCharactersForwardBtn")
    void onClickAdvanceCharsForwards(final ClickEvent event) {
        if (display != null) {
            display.advanceCharactersForward();
        }
    }

    @UiHandler("refreshBtn")
    void onClickRefresh(final ClickEvent event) {
        if (display != null) {
            display.refresh();
        }
    }

    /**
     * Let the page know that the table is loading. Call this method to clear
     * all data from the table and hide the current range when new data is being
     * loaded into the table.
     */
//    public void startLoading() {
    // TODO @AT caller can just update the values in HasCharacterData and call refresh
//        getDisplay().setRowCount(0, true);
//        lblFrom.setText("?");
//        lblTo.setText("?");
//        lblOf.setText("?");
//    }
    public void refreshNavigator() {

        refreshCharacterControls();
//        refreshProgressBar();
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

    public interface Binder extends UiBinder<Widget, CharacterNavigatorViewImpl> {
    }
}
