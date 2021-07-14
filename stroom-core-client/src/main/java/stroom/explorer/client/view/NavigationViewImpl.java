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
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.tab.client.view.Images;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class NavigationViewImpl extends ViewWithUiHandlers<NavigationUiHandlers> implements NavigationView {

    private final Widget widget;

    @UiField
    Button logo;
    @UiField
    Button menu;
    @UiField
    QuickFilter nameFilter;
    @UiField
    ScrollPanel scrollPanel;
    @UiField
    SimplePanel activityPanel;

    @Inject
    public NavigationViewImpl(final NavigationViewImpl.Binder binder,
                              final UiConfigCache uiConfigCache) {
        widget = binder.createAndBindUi(this);
        logo.getElement().setInnerHTML(Images.LOGO);
        menu.getElement().setInnerHTML(Images.MENU);

        uiConfigCache.get()
                .onSuccess(uiConfig ->
                        nameFilter.registerPopupTextProvider(() ->
                                QuickFilterTooltipUtil.createTooltip(
                                        "Explorer Quick Filter",
                                        ExplorerTreeFilter.FIELD_DEFINITIONS,
                                        uiConfig.getHelpUrl())));
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeQuickFilter(nameFilter.getText());
    }

    @UiHandler("logo")
    void onLogo(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            getUiHandlers().showMenu(logo.getElement());
        }
    }

    @UiHandler("menu")
    void onMenu(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            getUiHandlers().maximise();
        }
    }

    @Override
    public void setNavigationWidget(final Widget widget) {
        scrollPanel.setWidget(widget);
    }

    @Override
    public void setActivityWidget(final Widget widget) {
        this.activityPanel.setWidget(widget);
    }

    public interface Binder extends UiBinder<Widget, NavigationViewImpl> {

    }
}
