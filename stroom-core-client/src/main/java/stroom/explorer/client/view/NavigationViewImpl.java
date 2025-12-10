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

package stroom.explorer.client.view;

import stroom.explorer.client.presenter.NavigationPresenter.NavigationView;
import stroom.explorer.client.presenter.NavigationUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.function.Supplier;

public class NavigationViewImpl extends ViewWithUiHandlers<NavigationUiHandlers> implements NavigationView {

    private final Widget widget;

    @UiField(provided = true)
    FlowPanel layout;
    @UiField
    Button logo;
    @UiField
    Button mainMenuButton;
    @UiField
    QuickFilter nameFilter;
    @UiField
    FlowPanel buttonContainer;
    @UiField
    SimplePanel explorerTreeContainer;
    @UiField
    SimplePanel activityPanel;
    @UiField
    SpinnerSmall spinner;

    @Inject
    public NavigationViewImpl(final NavigationViewImpl.Binder binder) {
        layout = new FlowPanel();
        widget = binder.createAndBindUi(this);

        logo.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(
                SvgImage.LOGO,
                "navigation-logo-image"));

        mainMenuButton.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(
                SvgImage.MENU,
                "main-menu"));
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        nameFilter.registerPopupTextProvider(popupTextSupplier);
    }

    @UiHandler("logo")
    void onLogoClick(final ClickEvent event) {
        if (MouseUtil.isPrimary(event.getNativeEvent())) {
//            getUiHandlers().showAboutDialog();
            getUiHandlers().toggleMenu(event.getNativeEvent(), event.getRelativeElement());
        }
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeQuickFilter(nameFilter.getText());
    }

    @UiHandler("mainMenuButton")
    void onMainMenuButton(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            getUiHandlers().toggleMenu(event.getNativeEvent(), mainMenuButton.getElement());
        }
    }

    @Override
    public FlowPanel getButtonContainer() {
        return buttonContainer;
    }

    @Override
    public void setNavigationWidget(final Widget widget) {
        explorerTreeContainer.setWidget(widget);
    }

    @Override
    public void setActivityWidget(final Widget widget) {
        this.activityPanel.setWidget(widget);
    }

    @Override
    public void focusQuickFilter() {
        nameFilter.focus();
    }

    @Override
    public TaskMonitorFactory getTaskListener() {
        return spinner;
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, NavigationViewImpl> {

    }
}
