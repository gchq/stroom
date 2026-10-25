/*
 * Copyright 2021 Crown Copyright
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
import stroom.document.client.DocumentPluginRegistry;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.client.component.ExplorerTree;
import stroom.explorer.client.component.ExplorerTreeController;
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
import stroom.explorer.client.presenter.NavigationPresenter.NavigationView;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.main.client.event.ShowMainEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.query.shared.QueryDoc;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ActivityConfig;
import stroom.util.shared.NullSafe;
import stroom.view.shared.ViewDoc;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.menu.client.presenter.HideMenuEvent;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Supplier;

public class NavigationPresenter extends MyPresenterWidget<NavigationView>
        implements NavigationUiHandlers,
        RefreshExplorerTreeEvent.Handler,
        HighlightExplorerNodeEvent.Handler,
        ShowMainEvent.Handler,
        FocusExplorerFilterEvent.Handler,
        FocusExplorerTreeEvent.Handler {

    private final DocumentTypeCache documentTypeCache;
    private final TypeFilterPresenter typeFilterPresenter;
    private final CurrentActivity currentActivity;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private final ExplorerTree explorerTree;
    private final ExplorerTreeController treeController;
    private final SimplePanel activityOuter = new SimplePanel();
    private final Button activityButton = new Button();

    private final MenuItems menuItems;

    private final ToolbarButton locate;
    private final ToolbarButton find;
    private final ToolbarButton collapseAll;
    private final ToolbarButton expandAll;
    private final ToolbarButton add;
    private final ToolbarButton delete;
    private final ToolbarToggleButton filter;
    private final ToolbarToggleButton showAlertsBtn;
    private boolean menuVisible = false;
    private boolean hasActiveFilter = false;
    private boolean shown;
    private final DocumentPluginRegistry documentPluginRegistry;
    private DocRef selectedDoc;

    @Inject
    public NavigationPresenter(final EventBus eventBus,
                               final NavigationView view,
                               final MenuItems menuItems,
                               final RestFactory restFactory,
                               final Provider<ExplorerTreeController> explorerTreeControllerProvider,
                               final DocumentTypeCache documentTypeCache,
                               final DocumentPluginRegistry documentPluginRegistry,
                               final TypeFilterPresenter typeFilterPresenter,
                               final CurrentActivity currentActivity,
                               final UiConfigCache uiConfigCache,
                               final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        own(typeFilterPresenter);
        this.menuItems = menuItems;
        this.documentTypeCache = documentTypeCache;
        this.documentPluginRegistry = documentPluginRegistry;
        this.typeFilterPresenter = typeFilterPresenter;
        this.currentActivity = currentActivity;
        this.quickFilterContextHandlerFactory = quickFilterContextHandlerFactory;

        // The explorer's buttons: one toolbar, so one tab stop
        final Toolbar toolbar = getView().getToolbar();
        add = toolbar.button(SvgImage.ADD, "New Document", this::onNew);
        add.setOpensMenu(true);
        add.setEnabled(false);
        delete = toolbar.button(SvgImage.DELETE, "Delete Selected Documents", this::deleteItem);
        delete.setEnabled(false);
        showAlertsBtn = toolbar.toggle(SvgImage.EXCLAMATION, "Show Alerts", this::onShowAlerts);
        locate = toolbar.button(SvgImage.LOCATE, "Locate Current Item", this::onLocate);
        locate.setEnabled(false);
        expandAll = toolbar.button(SvgImage.EXPAND_ALL, "Expand All Folders", this::onExpandAll);
        collapseAll = toolbar.button(SvgImage.COLLAPSE_ALL, "Collapse All Folders", this::onCollapseAll);
        filter = toolbar.toggle(SvgImage.FILTER, "Filter Document Types", this::onFilter);
        filter.setPressed(hasActiveFilter);
        find = toolbar.button(SvgImage.FIND, "Find in Content", this::onFind);

        view.setUiHandlers(this);

        explorerTree = new ExplorerTree();
        explorerTree.setMultiSelect(true);
        treeController = make(explorerTreeControllerProvider).attach(explorerTree);
        treeController.setTaskMonitorFactory(getView().getTaskListener());
        treeController.setShowAlerts(showAlertsBtn.isPressed());

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

        // track the currently selected doc.
        listen(ContentTabSelectionChangeEvent.getType(), e -> {
            selectedDoc = documentPluginRegistry.getExplorerDocRef(e.getTabData());
            locate.setEnabled(selectedDoc != null);
        });

        // Register for refresh events.
        listen(RefreshExplorerTreeEvent.getType(), this);

        // Register for changes to the current activity.
        listen(ActivityChangedEvent.getType(), event -> updateActivitySummary());

        // Register for highlight events.
        listen(HighlightExplorerNodeEvent.getType(), this);

        // Register for events to focus the explorer tree filter
        listen(FocusExplorerFilterEvent.getType(), this);

        listen(FocusExplorerTreeEvent.getType(), this);

        // Deal with task listeners.
        final TaskMonitor taskMonitor = getView().getTaskListener().createTaskMonitor();
        listen(ExplorerStartTaskEvent.getType(), e ->
                taskMonitor.onStart(e.getTask()));
        listen(ExplorerEndTaskEvent.getType(), e ->
                taskMonitor.onEnd(e.getTask()));

        own(typeFilterPresenter.addSelectionListener(event -> {
            treeController.setIncludedTypeSet(typeFilterPresenter.getIncludedTypes().orElse(null));
            treeController.refresh();
        }));

        // Fire events from the explorer tree globally.
        own(explorerTree.getSelectionModel().addSelectionHandler(event -> {
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
        own(explorerTree.addContextMenuHandler(event -> getEventBus().fireEvent(event)));
        own(explorerTree.addItemKeyHandler(event -> getEventBus().fireEvent(event)));

        own(activityButton.addClickHandler(event -> currentActivity.showActivityChooser()));
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
        treeController.changeNameFilter(name);
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
                    // Named here, as it may be opened from the logo, which names nothing
                    .name("Main")
                    .items(items)
                    .popupPosition(popupPosition)
                    .opener(autoHidePartner)
                    .onHide(e -> menuVisible = false)
                    .fire(this);
        }
    }

    private void onNew() {
        newItem(add.getElement());
    }

    private void onShowAlerts(final boolean showAlerts) {
        treeController.setShowAlerts(showAlerts);
        treeController.refresh();
    }

    private void onLocate() {
        LocateDocEvent.fire(this, selectedDoc);
    }

    private void onExpandAll() {
        treeController.setForceSelection(explorerTree.getSelectionModel().getSelected());
        treeController.expandAll();
    }

    private void onCollapseAll() {
        treeController.setForceSelection(explorerTree.getSelectionModel().getSelected());
        treeController.collapseAll();
    }

    // The filter is only pressed when a filter has been set, not just when clicked
    private void onFilter(final boolean pressed) {
        showTypeFilter(filter.getElement());
    }

    private void onFind() {
        ShowFindInContentEvent.fire(this);
    }

    public void showTypeFilter(final Element target) {
        // Override the default behaviour of the toggle button as we only want
        // it to be ON if a filter has been set, not just when clicked
        filter.setPressed(hasActiveFilter);
        typeFilterPresenter.show(target, this::setFilterState);
    }

    private void setFilterState(final boolean hasActiveFilter) {
        this.hasActiveFilter = hasActiveFilter;
        filter.setPressed(hasActiveFilter);
    }

    @Override
    public void onShowMain(final ShowMainEvent event) {
        documentTypeCache.clear();
        // Set the data for the type filter.
        documentTypeCache.fetch(typeFilterPresenter::setDocumentTypes, getView().getTaskListener());

        treeController.reset();
        treeController.setRequiredPermissions(DocumentPermission.VIEW);
        treeController.setIncludedTypeSet(null);

        // Show the tree.
        if (!shown) {
            shown = true;
            treeController.refresh();
            getView().setNavigationWidget(explorerTree);
            treeController.setOnFilterError(getView()::setFilterError);
            getView().setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                    ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, this));
        }

        if (event.getInitialDocRef() != null) {
            OpenDocumentEvent.fire(this, event.getInitialDocRef(), true);
        }
    }

    @Override
    public void onHighlight(final HighlightExplorerNodeEvent event) {
        explorerTree.setSelectedItem(event.getExplorerNode());
        treeController.setEnsureVisible(event.getExplorerNode());
        treeController.refresh();
    }

    @Override
    public void onRefresh(final RefreshExplorerTreeEvent event) {
//        GWT.log("onRefresh " + event);
        treeController.refresh();
    }

    public void refresh() {
        treeController.refresh();
    }

    @Override
    public void onFocusExplorerFilter(final FocusExplorerFilterEvent event) {
        getView().focusQuickFilter();
    }

    @Override
    public void onFocusExplorerTree(final FocusExplorerTreeEvent event) {
        explorerTree.focus();
    }


    // --------------------------------------------------------------------------------


    public interface NavigationView extends View, HasUiHandlers<NavigationUiHandlers> {

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        /// @return The explorer's toolbar, for its buttons.
        Toolbar getToolbar();

        void setNavigationWidget(Widget widget);

        void setActivityWidget(Widget widget);

        void focusQuickFilter();

        /**
         * Show why the server rejected the filter, or clear it when passed null.
         * See {@code FetchExplorerNodeResult.filterError}.
         */
        void setFilterError(String filterError);

        void setQuickFilterContextHandler(QuickFilterContextHandler contextHandler);

        TaskMonitorFactory getTaskListener();
    }
}
