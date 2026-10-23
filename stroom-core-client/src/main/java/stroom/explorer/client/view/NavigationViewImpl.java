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

package stroom.explorer.client.view;

import stroom.explorer.client.presenter.NavigationPresenter.NavigationView;
import stroom.explorer.client.presenter.NavigationUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.presenter.client.ViewWithUiHandlers;
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

import java.util.function.Supplier;

public class NavigationViewImpl extends ViewWithUiHandlers<NavigationUiHandlers> implements NavigationView {

    private final Widget widget;

    @UiField(provided = true)
    FlowPanel layout;
    @UiField
    Button logo;
    @UiField
    ToolbarButton mainMenuButton;
    @UiField
    QuickFilter nameFilter;
    @UiField
    Toolbar toolbar;
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
        // Named for screen readers, as its placeholder is only a hint, lost once something is typed
        nameFilter.setAccessibleName("Filter explorer");

        logo.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(
                SvgImage.LOGO,
                "navigation-logo-image"));
        // A click on the logo opens the main menu, as the Main Menu button beside it does, so for
        // the keyboard and screen readers it would be a second, nameless Main Menu: it is left out
        // of the tab order (-2, as FocusWidget.onAttach turns -1 back into 0) and hidden from them
        logo.getElement().setTabIndex(-2);
        logo.getElement().setAttribute("aria-hidden", "true");
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
    public Toolbar getToolbar() {
        return toolbar;
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
    public void setFilterError(final String filterError) {
        nameFilter.setFilterError(filterError);
    }

    @Override
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        nameFilter.setContextHandler(contextHandler);
    }

    @Override
    public TaskMonitorFactory getTaskListener() {
        return spinner;
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, NavigationViewImpl> {

    }
}
