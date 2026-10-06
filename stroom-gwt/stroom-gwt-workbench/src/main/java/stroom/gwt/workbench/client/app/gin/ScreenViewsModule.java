/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin;

import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.data.client.presenter.EditExpressionPresenter.EditExpressionView;
import stroom.data.client.presenter.ExpressionPresenter;
import stroom.data.client.presenter.ExpressionPresenter.ExpressionView;
import stroom.data.client.view.EditExpressionViewImpl;
import stroom.data.client.view.ExpressionViewImpl;
import stroom.data.grid.client.PagerView;
import stroom.data.grid.client.PagerViewImpl;
import stroom.data.grid.client.PagerViewWithHeading;
import stroom.data.grid.client.PagerViewWithHeadingImpl;
import stroom.data.grid.client.WrapperView;
import stroom.data.grid.client.WrapperViewImpl;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.editor.client.presenter.SingleLineEditorPresenter;
import stroom.editor.client.presenter.SingleLineEditorView;
import stroom.editor.client.view.EditorViewImpl;
import stroom.editor.client.view.SingleLineEditorViewImpl;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.view.LinkTabPanelViewImpl;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.iframe.client.presenter.IFramePresenter.IFrameView;
import stroom.iframe.client.view.IFrameViewImpl;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionTreePresenter.ExpressionTreeView;
import stroom.query.client.ExpressionTreeViewImpl;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.datepicker.client.DateTimePopup.DateTimeView;
import stroom.widget.datepicker.client.DateTimeViewImpl;
import stroom.widget.datepicker.client.TimePopup;
import stroom.widget.datepicker.client.TimePopup.TimeView;
import stroom.widget.datepicker.client.TimeViewImpl;
import stroom.widget.dropdowntree.client.view.DropDownView;
import stroom.widget.dropdowntree.client.view.DropDownViewImpl;
import stroom.widget.dropdowntree.client.view.ExplorerPopupView;
import stroom.widget.dropdowntree.client.view.ExplorerPopupViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogView;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageViewImpl;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.menu.client.presenter.MenuViewImpl;
import stroom.widget.popup.client.presenter.TextBoxPopup;
import stroom.widget.popup.client.presenter.TextBoxPopup.TextBoxView;
import stroom.widget.popup.client.view.TextBoxViewImpl;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.view.CurveTabLayoutViewImpl;
import stroom.widget.tab.client.view.LinkTabsLayoutViewImpl;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipPresenter.TooltipView;
import stroom.widget.tooltip.client.view.TooltipViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The views and small presenters that Stroom binds for every screen, copied from Stroom's
/// `AppModule` and the shared widgets that other Stroom modules (e.g. `StreamStoreModule`) bind,
/// but without plugins or the app shell, so that e.g. a list presenter's `PagerView` is created
/// as in Stroom.
///
/// GIN checks the whole graph of every binding of an injector's modules, used or not, so a widget
/// bound here must be left out of the area modules (a key bound twice fails the GIN compile).
/// Add a binding here (copied from Stroom's module) when a view or presenter is a widget shared
/// by several areas' screens.
public class ScreenViewsModule extends AbstractPresenterModule {

    /// Binds the shared views and presenters.
    @Override
    protected void configure() {
        // AppModule: widgets
        bindSharedView(CurveTabLayoutView.class, CurveTabLayoutViewImpl.class);
        bindSharedView(PagerView.class, PagerViewImpl.class);
        bindSharedView(PagerViewWithHeading.class, PagerViewWithHeadingImpl.class);
        bindSharedView(LinkTabPanelView.class, LinkTabPanelViewImpl.class);
        bindPresenterWidget(TextBoxPopup.class, TextBoxView.class, TextBoxViewImpl.class);
        // AppModule: menus and tool tips
        bindPresenterWidget(MenuPresenter.class, MenuView.class, MenuViewImpl.class);
        bindPresenterWidget(TooltipPresenter.class, TooltipView.class, TooltipViewImpl.class);
        bindPresenterWidget(IFramePresenter.class, IFrameView.class, IFrameViewImpl.class);

        // SecurityModule
        bindSharedView(QuickFilterDialogView.class, QuickFilterDialogViewImpl.class);
        bindSharedView(QuickFilterPageView.class, QuickFilterPageViewImpl.class);

        // MonitoringModule
        bindSharedView(WrapperView.class, WrapperViewImpl.class);
        bindPresenterWidget(DateTimePopup.class, DateTimeView.class, DateTimeViewImpl.class);
        bindPresenterWidget(TimePopup.class, TimeView.class, TimeViewImpl.class);

        // StreamStoreModule: editors, expressions and the explorer drop downs
        bindPresenterWidget(EditorPresenter.class, EditorView.class, EditorViewImpl.class);
        bindPresenterWidget(SingleLineEditorPresenter.class, SingleLineEditorView.class,
                SingleLineEditorViewImpl.class);
        bindPresenterWidget(ExpressionPresenter.class, ExpressionView.class, ExpressionViewImpl.class);
        bindSharedView(DropDownView.class, DropDownViewImpl.class);
        bindSharedView(ExplorerPopupView.class, ExplorerPopupViewImpl.class);

        // QueryModule, PolicyModule and TableModule
        bindPresenterWidget(ExpressionTreePresenter.class, ExpressionTreeView.class, ExpressionTreeViewImpl.class);
        bindPresenterWidget(EditExpressionPresenter.class, EditExpressionView.class, EditExpressionViewImpl.class);

        // DashboardModule
        bindSharedView(LinkTabsLayoutView.class, LinkTabsLayoutViewImpl.class);
    }
}
