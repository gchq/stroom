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

package stroom.explorer.client.presenter;

import stroom.about.client.event.ShowAboutEvent;
import stroom.activity.client.ActivityChangedEvent;
import stroom.activity.client.CurrentActivity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.Activity.Prop;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.annotation.client.CreateAnnotationEvent;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.core.client.MenuKeys;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.client.event.CreateNewDocumentEvent;
import stroom.explorer.client.event.ExplorerEndTaskEvent;
import stroom.explorer.client.event.ExplorerStartTaskEvent;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.FocusExplorerFilterEvent;
import stroom.explorer.client.event.FocusExplorerTreeEvent;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.client.event.LocateDocEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.event.ShowFindInContentEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationProxy;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationView;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.main.client.event.ShowMainEvent;
import stroom.main.client.presenter.MainPresenter;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.query.shared.QueryDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ActivityConfig;
import stroom.util.shared.NullSafe;
import stroom.view.shared.ViewDoc;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.menu.client.presenter.HideMenuEvent;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;

import java.util.List;
import java.util.function.Supplier;

public class NavigationPresenter extends MyPresenter<NavigationView, NavigationProxy>
        implements NavigationUiHandlers,
        RefreshExplorerTreeEvent.Handler,
        HighlightExplorerNodeEvent.Handler,
        ShowMainEvent.Handler,
        FocusExplorerFilterEvent.Handler,
        FocusExplorerTreeEvent.Handler {

    private final DocumentTypeCache documentTypeCache;
    private final TypeFilterPresenter typeFilterPresenter;
    private final CurrentActivity currentActivity;
    private final ExplorerTree explorerTree;
    private final SimplePanel activityOuter = new SimplePanel();
    private final Button activityButton = new Button();

    private final MenuItems menuItems;

    private final InlineSvgButton locate;
    private final InlineSvgButton find;
    private final InlineSvgButton collapseAll;
    private final InlineSvgButton expandAll;
    private final InlineSvgButton add;
    private final InlineSvgButton delete;
    private final InlineSvgToggleButton filter;
    private final InlineSvgToggleButton showAlertsBtn;
    private boolean menuVisible = false;
    private boolean hasActiveFilter = false;
    private DocRef selectedDoc;

    @Inject
    public NavigationPresenter(final EventBus eventBus,
                               final NavigationView view,
                               final NavigationProxy proxy,
                               final MenuItems menuItems,
                               final RestFactory restFactory,
                               final DocumentTypeCache documentTypeCache,
                               final TypeFilterPresenter typeFilterPresenter,
                               final CurrentActivity currentActivity,
                               final UiConfigCache uiConfigCache) {
        super(eventBus, view, proxy);
        this.menuItems = menuItems;
        this.documentTypeCache = documentTypeCache;
        this.typeFilterPresenter = typeFilterPresenter;
        this.currentActivity = currentActivity;

        add = new InlineSvgButton();
        add.setSvg(SvgImage.ADD);
        add.getElement().addClassName("navigation-header-button add");
        add.setTitle("New");
        add.setEnabled(false);

        delete = new InlineSvgButton();
        delete.setSvg(SvgImage.DELETE);
        delete.getElement().addClassName("navigation-header-button delete");
        delete.setTitle("Delete");
        delete.setEnabled(false);

        filter = new InlineSvgToggleButton();
        filter.setState(hasActiveFilter);
        filter.setSvg(SvgImage.FILTER);
        filter.getElement().addClassName("navigation-header-button filter");
        filter.setTitle("Filter Types");
        filter.setEnabled(true);

        showAlertsBtn = new InlineSvgToggleButton();
        showAlertsBtn.setOff();
        showAlertsBtn.setSvg(SvgImage.EXCLAMATION);
        showAlertsBtn.getElement().addClassName("navigation-header-button show-alerts");
        showAlertsBtn.setTitle("Toggle Alerts");
        showAlertsBtn.setEnabled(true);

        collapseAll = new InlineSvgButton();
        collapseAll.setSvg(SvgImage.COLLAPSE_ALL);
        collapseAll.getElement().addClassName("navigation-header-button explorer-collapse-all");
        collapseAll.setTitle("Collapse All");
        collapseAll.setEnabled(true);

        expandAll = new InlineSvgButton();
        expandAll.setSvg(SvgImage.EXPAND_ALL);
        expandAll.getElement().addClassName("navigation-header-button explorer-expand-all");
        expandAll.setTitle("Expand All");
        expandAll.setEnabled(true);

        find = new InlineSvgButton();
        find.setSvg(SvgImage.FIND);
        find.getElement().addClassName("navigation-header-button find");
        find.setTitle("Find In Content");
        find.setEnabled(true);

        locate = new InlineSvgButton();
        locate.setSvg(SvgImage.LOCATE);
        locate.getElement().addClassName("navigation-header-button locate-in-explorer");
        locate.setTitle("Locate Current Item");
        locate.setEnabled(false);

        final FlowPanel buttons = getView().getButtonContainer();
        buttons.add(add);
        buttons.add(delete);
        buttons.add(showAlertsBtn);
        buttons.add(locate);
        buttons.add(expandAll);
        buttons.add(collapseAll);
        buttons.add(filter);
        buttons.add(find);

        view.setUiHandlers(this);

        explorerTree = new ExplorerTree(
                restFactory,
                getView().getTaskListener(),
                true,
                showAlertsBtn.getState());

        // Add views.
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final ActivityConfig activityConfig = uiConfig.getActivity();
                if (activityConfig.isEnabled()) {
                    updateActivitySummary();
                    activityButton.setStyleName("activityButton dashboard-panel");
                    activityOuter.setStyleName("activityOuter");
                    activityOuter.setWidget(activityButton);

                    getView().setActivityWidget(activityOuter);
                }
                showAlertsBtn.setVisible(uiConfig.isDependencyWarningsEnabled());

                getView().registerPopupTextProvider(() ->
                        QuickFilterTooltipUtil.createTooltip(
                                "Explorer Quick Filter",
                                ExplorerTreeFilter.FIELD_DEFINITIONS,
                                uiConfig.getHelpUrl()));
            }
        }, getView().getTaskListener());

        KeyBinding.addCommand(Action.FOCUS_EXPLORER_FILTER, () ->
                FocusExplorerFilterEvent.fire(this));
        KeyBinding.addCommand(Action.GOTO_EXPLORER_TREE, () ->
                FocusExplorerTreeEvent.fire(this));
        // Binds for creating a document of a given type
        KeyBinding.addCommand(Action.CREATE_ANNOTATION, () ->
                CreateAnnotationEvent.fire(this));
        KeyBinding.addCommand(Action.CREATE_ELASTIC_INDEX, () ->
                CreateNewDocumentEvent.fire(this, ElasticIndexDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_DASHBOARD, () ->
                CreateNewDocumentEvent.fire(this, DashboardDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_FEED, () ->
                CreateNewDocumentEvent.fire(this, FeedDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_FOLDER, () ->
                CreateNewDocumentEvent.fire(this, ExplorerConstants.FOLDER_TYPE));
        KeyBinding.addCommand(Action.CREATE_DICTIONARY, () ->
                CreateNewDocumentEvent.fire(this, DictionaryDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_LUCENE_INDEX, () ->
                CreateNewDocumentEvent.fire(this, LuceneIndexDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_DOCUMENTATION, () ->
                CreateNewDocumentEvent.fire(this, DocumentationDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_PIPELINE, () ->
                CreateNewDocumentEvent.fire(this, PipelineDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_QUERY, () ->
                CreateNewDocumentEvent.fire(this, QueryDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_ANALYTIC_RULE, () ->
                CreateNewDocumentEvent.fire(this, AnalyticRuleDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_TEXT_CONVERTER, () ->
                CreateNewDocumentEvent.fire(this, TextConverterDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_VIEW, () ->
                CreateNewDocumentEvent.fire(this, ViewDoc.TYPE));
        KeyBinding.addCommand(Action.CREATE_XSLT, () ->
                CreateNewDocumentEvent.fire(this, XsltDoc.TYPE));
    }

    @Override
    protected void onBind() {
        super.onBind();

        // track the currently selected doc.
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(), e -> {
            selectedDoc = null;
            if (e.getTabData() instanceof final DocumentTabData documentTabData) {
                selectedDoc = documentTabData.getDocRef();
            }
            locate.setEnabled(selectedDoc != null);
        }));
        registerHandler(collapseAll.addClickHandler((e) -> {
            explorerTree.getTreeModel().setForceSelection(explorerTree.getSelectionModel().getSelected());
            explorerTree.getTreeModel().collapseAll();
        }));
        registerHandler(expandAll.addClickHandler((e) -> {
            explorerTree.getTreeModel().setForceSelection(explorerTree.getSelectionModel().getSelected());
            explorerTree.getTreeModel().expandAll();
        }));
        registerHandler(locate.addClickHandler((e) -> LocateDocEvent.fire(this, selectedDoc)));
        registerHandler(find.addClickHandler((e) -> ShowFindInContentEvent.fire(this)));
        registerHandler(add.addClickHandler((e) -> newItem(add.getElement())));
        registerHandler(delete.addClickHandler((e) -> deleteItem()));
        registerHandler(filter.addClickHandler((e) -> showTypeFilter(filter.getElement())));
        registerHandler(showAlertsBtn.addClickHandler((e) -> {
            explorerTree.setShowAlerts(showAlertsBtn.getState());
            explorerTree.refresh();
        }));

        // Register for refresh events.
        registerHandler(getEventBus().addHandler(RefreshExplorerTreeEvent.getType(), this));

        // Register for changes to the current activity.
        registerHandler(getEventBus().addHandler(ActivityChangedEvent.getType(), event -> updateActivitySummary()));

        // Register for highlight events.
        registerHandler(getEventBus().addHandler(HighlightExplorerNodeEvent.getType(), this));

        // Register for events to focus the explorer tree filter
        registerHandler(getEventBus().addHandler(FocusExplorerFilterEvent.getType(), this));

        registerHandler(getEventBus().addHandler(FocusExplorerTreeEvent.getType(), this));

        // Deal with task listeners.
        final TaskMonitor taskMonitor = getView().getTaskListener().createTaskMonitor();
        registerHandler(getEventBus().addHandler(ExplorerStartTaskEvent.getType(), e ->
                taskMonitor.onStart(e.getTask())));
        registerHandler(getEventBus().addHandler(ExplorerEndTaskEvent.getType(), e ->
                taskMonitor.onEnd(e.getTask())));

        registerHandler(typeFilterPresenter.addDataSelectionHandler(event -> explorerTree.setIncludedTypeSet(
                typeFilterPresenter.getIncludedTypes().orElse(null))));

        // Fire events from the explorer tree globally.
        registerHandler(explorerTree.getSelectionModel().addSelectionHandler(event -> {
            getEventBus().fireEvent(new ExplorerTreeSelectEvent(
                    explorerTree.getSelectionModel(),
                    event.getSelectionType()));
            final ExplorerNode selectedNode = explorerTree.getSelectionModel().getSelected();
            final boolean enabled = NullSafe.hasItems(explorerTree.getSelectionModel().getSelectedItems()) &&
                                    !ExplorerConstants.isFavouritesNode(selectedNode) &&
                                    !ExplorerConstants.isSystemNode(selectedNode);
            add.setEnabled(enabled);
            delete.setEnabled(enabled);
        }));
        registerHandler(explorerTree.addContextMenuHandler(event -> getEventBus().fireEvent(event)));

        registerHandler(activityButton.addClickHandler(event -> currentActivity.showActivityChooser()));
    }

    private void updateActivitySummary() {
        currentActivity.getActivity(activity -> {
            final StringBuilder sb = new StringBuilder("<h2>Current Activity</h2>");

            if (activity != null) {
                final ActivityDetails activityDetails = activity.getDetails();
                for (final Prop prop : activityDetails.getProperties()) {
                    if (prop.isShowInSelection()) {
                        sb.append("<b>");
                        sb.append(prop.getName());
                        sb.append(": </b>");
                        sb.append(prop.getValue());
                        sb.append("</br>");
                    }
                }
            } else {
                sb.append("<b>");
                sb.append("none");
            }

            activityButton.setHTML(sb.toString());
        }, getView().getTaskListener());
    }

    public void newItem(final Element element) {
        final int x = element.getAbsoluteLeft() - 1;
        final int y = element.getAbsoluteTop() + element.getOffsetHeight() + 1;
        final PopupPosition popupPosition = new PopupPosition(x, y);
        ShowNewMenuEvent.fire(this, element, popupPosition);
    }

    public void deleteItem() {
        if (NullSafe.hasItems(explorerTree.getSelectionModel().getSelectedItems())) {
            ExplorerTreeDeleteEvent.fire(this);
        }
    }

    @Override
    public void changeQuickFilter(final String name) {
        explorerTree.changeNameFilter(name);
    }

    @Override
    public void toggleMenu(final NativeEvent event, final Element target) {
        menuVisible = !menuVisible;
        if (menuVisible) {
            final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft(),
                    target.getAbsoluteBottom());
            showMenuItems(
                    popupPosition,
                    target);
        } else {
            HideMenuEvent
                    .builder()
                    .fire(this);
        }
    }

    @Override
    public void showAboutDialog() {
        ShowAboutEvent.fire(this);
    }

    private void showMenuItems(final PopupPosition popupPosition,
                               final Element autoHidePartner) {
        // Clear the current menus.
        menuItems.clear();
        // Tell all plugins to add new menu items.
        BeforeRevealMenubarEvent.fire(this, menuItems);
        final List<Item> items = menuItems.getMenuItems(MenuKeys.MAIN_MENU);
        if (NullSafe.hasItems(items)) {
            ShowMenuEvent
                    .builder()
                    .items(items)
                    .popupPosition(popupPosition)
                    .addAutoHidePartner(autoHidePartner)
                    .onHide(e -> menuVisible = false)
                    .fire(this);
        }
    }

    public void showTypeFilter(final Element target) {
        // Override the default behaviour of the toggle button as we only want
        // it to be ON if a filter has been set, not just when clicked
        filter.setState(hasActiveFilter);
        typeFilterPresenter.show(target, this::setFilterState);
    }

    private void setFilterState(final boolean hasActiveFilter) {
        this.hasActiveFilter = hasActiveFilter;
        filter.setState(hasActiveFilter);
    }

    @ProxyEvent
    @Override
    public void onShowMain(final ShowMainEvent event) {
        documentTypeCache.clear();
        // Set the data for the type filter.
        documentTypeCache.fetch(typeFilterPresenter::setDocumentTypes, getView().getTaskListener());

        explorerTree.getTreeModel().reset();
        explorerTree.getTreeModel().setRequiredPermissions(DocumentPermission.VIEW);
        explorerTree.getTreeModel().setIncludedTypeSet(null);

        // Show the tree.
        forceReveal();

        if (event.getInitialDocRef() != null) {
            OpenDocumentEvent.fire(this, event.getInitialDocRef(), true);
        }
    }

    @Override
    public void onHighlight(final HighlightExplorerNodeEvent event) {
        explorerTree.setSelectedItem(event.getExplorerNode());
        explorerTree.getTreeModel().setEnsureVisible(event.getExplorerNode());
        explorerTree.getTreeModel().refresh();
    }

    @Override
    public void onRefresh(final RefreshExplorerTreeEvent event) {
//        GWT.log("onRefresh " + event);
        explorerTree.getTreeModel().refresh();
    }

    public void refresh() {
        explorerTree.getTreeModel().refresh();
    }

    @Override
    protected void revealInParent() {
        explorerTree.getTreeModel().refresh();
        getView().setNavigationWidget(explorerTree);
        RevealContentEvent.fire(this, MainPresenter.EXPLORER, this);
    }

    @Override
    public void onFocusExplorerFilter(final FocusExplorerFilterEvent event) {
        getView().focusQuickFilter();
    }

    @Override
    public void onFocusExplorerTree(final FocusExplorerTreeEvent event) {
        explorerTree.focus();
    }

    @ProxyCodeSplit
    public interface NavigationProxy extends Proxy<NavigationPresenter> {

    }


    // --------------------------------------------------------------------------------


    public interface NavigationView extends View, HasUiHandlers<NavigationUiHandlers> {

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        FlowPanel getButtonContainer();

        void setNavigationWidget(Widget widget);

        void setActivityWidget(Widget widget);

        void focusQuickFilter();

        TaskMonitorFactory getTaskListener();
    }
}
