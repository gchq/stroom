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

package stroom.main.client.view;

import stroom.main.client.presenter.MainPresenter;
import stroom.main.client.presenter.MainPresenter.SpinnerDisplay;
import stroom.main.client.presenter.MainUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.ResizeLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Objects;

public class MainViewImpl extends ViewWithUiHandlers<MainUiHandlers> implements MainPresenter.MainView {

    private final Widget widget;
    @UiField
    FocusPanel root;
    @UiField
    SimplePanel banner;
    @UiField
    FlowPanel main;
    @UiField
    Spinner spinner;
    @UiField
    InlineSvgButton menu;
    @UiField
    ResizeLayoutPanel contentPanel;
    private Widget maximisedWidget;
    private int splitPos = 300;
    private ThinSplitLayoutPanel splitPanel;
    private Widget westWidget;
    private Widget centerWidget;
    private String currentBanner;

    @Inject
    public MainViewImpl(final Binder binder) {
        this.widget = binder.createAndBindUi(this);
        banner.setVisible(false);
        menu.setSvg(SvgImage.ELLIPSES_VERTICAL);
        widget.sinkEvents(Event.KEYEVENTS);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (slot == MainPresenter.EXPLORER) {
            westWidget = content;
            showSplit();
        } else if (slot == MainPresenter.CONTENT) {
            centerWidget = content;
            showSplit();
        } else {
            super.setInSlot(slot, content);
        }
    }

    @Override
    public void maximise(final View view) {
        if (view == null) {

            if (maximisedWidget == null) {
                // Remember split panel.
                if (westWidget != null) {
                    splitPos = westWidget.getOffsetWidth();
                }

                // Maximise the passed view.
                centerWidget.getElement().addClassName("maximised");
                contentPanel.setWidget(centerWidget);

                // Clear the split panel.
                hideSplit();
                maximisedWidget = centerWidget;

                if (maximisedWidget instanceof Focus) {
                    ((Focus) maximisedWidget).focus();
                }
            } else {
                centerWidget.getElement().removeClassName("maximised");

                // Restore the view.
                showSplit();
                maximisedWidget = null;

                if (westWidget instanceof Focus) {
                    ((Focus) westWidget).focus();
                }
            }

        } else {
            final Widget widget = view.asWidget();
            if (maximisedWidget == null || maximisedWidget != widget) {
                // Remember split panel.
                if (westWidget != null) {
                    splitPos = westWidget.getOffsetWidth();
                }

                // Maximise the passed view.
                contentPanel.setWidget(widget);

                // Clear the split panel.
                hideSplit();
                maximisedWidget = widget;

                if (maximisedWidget instanceof Focus) {
                    ((Focus) maximisedWidget).focus();
                }
            } else {
                // Restore the view.
                showSplit();
                maximisedWidget = null;

                if (westWidget instanceof Focus) {
                    ((Focus) westWidget).focus();
                }
            }
        }
    }

    private void showSplit() {
        // Ensure we the split position isn't too small.
        if (splitPos < 10) {
            splitPos = 10;
        }

        splitPanel = new ThinSplitLayoutPanel();
        splitPanel.addStyleName("mainViewImpl-splitPanel");
        if (westWidget != null) {
            splitPanel.addWest(westWidget, splitPos);
        }
        if (centerWidget != null) {
            splitPanel.add(centerWidget);
        }

        contentPanel.clear();
        contentPanel.setWidget(splitPanel);
    }

    private void hideSplit() {
        if (splitPanel != null) {
            splitPanel.clear();
            splitPanel = null;
        }
    }

    @Override
    public SpinnerDisplay getSpinner() {
        return spinner;
    }

    @Override
    public void setBorderStyle(final String style) {
        if (style != null && !style.isEmpty()) {
            root.getElement().setPropertyString("style", style);
        }
    }

    @Override
    public void setSelectedTabColour(final String colour) {
        if (colour != null && !colour.isBlank()) {
            DynamicStyles.put(SafeHtmlUtils.fromTrustedString(".curveTab-selected"),
                    SafeStylesUtils.fromTrustedNameAndValue("border-bottom-color", colour));
        }
    }

    @Override
    public void setBanner(final String text) {
        if (!Objects.equals(currentBanner, text)) {
            currentBanner = text;
            if (text == null || text.trim().isEmpty()) {
                main.getElement().getStyle().setTop(0, Unit.PX);
                banner.setVisible(false);
                banner.getElement().setInnerText("");
            } else {
                main.getElement().getStyle().setTop(20, Unit.PX);
                banner.setVisible(true);
                banner.getElement().setInnerText(text);
            }
        }
    }

    @UiHandler("menu")
    void onMenu(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            getUiHandlers().showMenu(event.getNativeEvent(), menu.getElement());
        }
    }

    public interface Binder extends UiBinder<Widget, MainViewImpl> {

    }
}
