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

package stroom.explorer.client.component;

import stroom.activity.client.ActivityChangedEvent;
import stroom.activity.client.ActivitySummary;
import stroom.activity.client.CurrentActivity;
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
import stroom.explorer.client.DocumentTypeCache;
import stroom.explorer.client.component.Navigation.ExplorerAction;
import stroom.explorer.client.event.CreateNewDocumentEvent;
import stroom.explorer.client.event.ExplorerEndTaskEvent;
import stroom.explorer.client.event.ExplorerStartTaskEvent;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.client.event.LocateDocEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.event.ShowFindInContentEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
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
import stroom.task.client.TaskMonitor;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ActivityConfig;
import stroom.util.shared.NullSafe;
import stroom.view.shared.ViewDoc;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.HideMenuEvent;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuItems;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// Shows the explorer: fills its tree and toolbar, filters it as the user asks, opens the main menu
/// and the documents chosen, and shows the current activity.
public class NavigationController extends Controller
        implements RefreshExplorerTreeEvent.Handler,
        HighlightExplorerNodeEvent.Handler,
        ShowMainEvent.Handler {

    private final Navigation view;

    private final DocumentTypeCache documentTypeCache;
    private final TypeFilterController typeFilter;
    private final CurrentActivity currentActivity;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private final ExplorerTree explorerTree;
    private final ExplorerTreeController treeController;

    private final MenuItems menuItems;

    private boolean menuVisible = false;
    private boolean hasActiveFilter = false;
    private boolean shown;
    private boolean activityShown;
    private final DocumentPluginRegistry documentPluginRegistry;
    private DocRef selectedDoc;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             Its component.
    /// @param menuItems                        The menu items.
    /// @param restFactory                      Makes REST requests.
    /// @param explorerTreeControllerProvider   Makes the explorer tree controller.
    /// @param documentTypeCache                The document type cache.
    /// @param documentPluginRegistry           The document plugin registry.
    /// @param typeFilter                       The type filter.
    /// @param currentActivity                  The current activity.
    /// @param uiConfigCache                    The UI's configuration.
    /// @param quickFilterContextHandlerFactory Adds the quick filter's history and Advanced Query.
    @Inject
    public NavigationController(final EventBus eventBus,
                               final Navigation view,
                               final MenuItems menuItems,
                               final RestFactory restFactory,
                               final Provider<ExplorerTreeController> explorerTreeControllerProvider,
                               final DocumentTypeCache documentTypeCache,
                               final DocumentPluginRegistry documentPluginRegistry,
                               final TypeFilterController typeFilter,
                               final CurrentActivity currentActivity,
                               final UiConfigCache uiConfigCache,
                               final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(typeFilter);
        this.menuItems = menuItems;
        this.documentTypeCache = documentTypeCache;
        this.documentPluginRegistry = documentPluginRegistry;
        this.typeFilter = typeFilter;
        this.currentActivity = currentActivity;
        this.quickFilterContextHandlerFactory = quickFilterContextHandlerFactory;

        view.setOnAction(this::onAction);
        view.setActionPressed(ExplorerAction.FILTER, hasActiveFilter);
        view.setOnFilterChange(this::changeQuickFilter);
        view.setOnToggleMenu(this::toggleMenu);
        view.setOnActivity(currentActivity::showActivityChooser);

        explorerTree = view.getExplorerTree();
        treeController = make(explorerTreeControllerProvider).attach(explorerTree);
        treeController.setTaskMonitorFactory(view.getTaskListener());
        treeController.setShowAlerts(view.isActionPressed(ExplorerAction.SHOW_ALERTS));

        // Add views.
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final ActivityConfig activityConfig = uiConfig.getActivity();
                if (activityConfig.isEnabled()) {
                    // Empty until the activity's summary arrives
                    view.setActivitySummary(SafeHtmlUtils.EMPTY_SAFE_HTML);
                    activityShown = true;
                    updateActivitySummary();
                }
                view.setActionVisible(ExplorerAction.SHOW_ALERTS, uiConfig.isDependencyWarningsEnabled());

                view.registerPopupTextProvider(() ->
                        QuickFilterTooltipUtil.createTooltip(
                                "Explorer Quick Filter",
                                ExplorerTreeFilter.FIELD_DEFINITIONS,
                                uiConfig.getHelpUrl()));
            }
        }, view.getTaskListener());

        KeyBinding.addCommand(Action.FOCUS_EXPLORER_FILTER, view::focusQuickFilter);
        KeyBinding.addCommand(Action.GOTO_EXPLORER_TREE, () -> explorerTree.focus());
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
            view.setActionEnabled(ExplorerAction.LOCATE, selectedDoc != null);
        });

        // Register for refresh events.
        listen(RefreshExplorerTreeEvent.getType(), this);

        // Register for changes to the current activity.
        listen(ActivityChangedEvent.getType(), event -> updateActivitySummary());

        // Register for highlight events.
        listen(HighlightExplorerNodeEvent.getType(), this);


        // Deal with task listeners.
        final TaskMonitor taskMonitor = view.getTaskListener().createTaskMonitor();
        listen(ExplorerStartTaskEvent.getType(), e ->
                taskMonitor.onStart(e.getTask()));
        listen(ExplorerEndTaskEvent.getType(), e ->
                taskMonitor.onEnd(e.getTask()));

        own(typeFilter.addSelectionListener(includedTypes -> {
            treeController.setIncludedTypeSet(includedTypes.orElse(null));
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
            view.setActionEnabled(ExplorerAction.NEW, enabled);
            view.setActionEnabled(ExplorerAction.DELETE, enabled);
        }));
        own(explorerTree.addContextMenuHandler(event -> getEventBus().fireEvent(event)));
        own(explorerTree.addItemKeyHandler(event -> getEventBus().fireEvent(event)));

    }

    private void updateActivitySummary() {
        currentActivity.getActivity(activity -> {
            if (activityShown) {
                view.setActivitySummary(ActivitySummary.toHtml(activity));
            }
        }, view.getTaskListener());
    }

    /// Opens the New menu beside an element.
    ///
    /// @param element The element, e.g. the New button.
    public void newItem(final Element element) {
        final int x = element.getAbsoluteLeft() - 1;
        final int y = element.getAbsoluteTop() + element.getOffsetHeight() + 1;
        final PopupPosition popupPosition = new PopupPosition(x, y);
        ShowNewMenuEvent.fire(this, element, popupPosition);
    }

    /// Deletes the documents selected in the tree, once the user confirms.
    public void deleteItem() {
        if (NullSafe.hasItems(explorerTree.getSelectionModel().getSelectedItems())) {
            ExplorerTreeDeleteEvent.fire(this);
        }
    }

    private void changeQuickFilter(final String name) {
        treeController.changeNameFilter(name);
    }

    private void toggleMenu(final Element target) {
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

    private void onAction(final ExplorerAction action) {
        switch (action) {
            case NEW -> newItem(view.getActionElement(ExplorerAction.NEW));
            case DELETE -> deleteItem();
            case SHOW_ALERTS -> onShowAlerts(view.isActionPressed(ExplorerAction.SHOW_ALERTS));
            case LOCATE -> onLocate();
            case EXPAND_ALL -> onExpandAll();
            case COLLAPSE_ALL -> onCollapseAll();
            case FILTER -> onFilter();
            case FIND -> onFind();
        }
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
    private void onFilter() {
        showTypeFilter(view.getActionElement(ExplorerAction.FILTER));
    }

    private void onFind() {
        ShowFindInContentEvent.fire(this);
    }

    /// Shows the document type filter beside an element.
    ///
    /// @param target The element, e.g. the filter button.
    public void showTypeFilter(final Element target) {
        // Override the default behaviour of the toggle button as we only want
        // it to be ON if a filter has been set, not just when clicked
        view.setActionPressed(ExplorerAction.FILTER, hasActiveFilter);
        typeFilter.show(target, this::setFilterState);
    }

    private void setFilterState(final boolean hasActiveFilter) {
        this.hasActiveFilter = hasActiveFilter;
        view.setActionPressed(ExplorerAction.FILTER, hasActiveFilter);
    }

    @Override
    public void onShowMain(final ShowMainEvent event) {
        documentTypeCache.clear();
        // Set the data for the type filter.
        documentTypeCache.fetch(typeFilter::setDocumentTypes, view.getTaskListener());

        treeController.reset();
        treeController.setRequiredPermissions(DocumentPermission.VIEW);
        treeController.setIncludedTypeSet(null);

        // Show the tree.
        if (!shown) {
            shown = true;
            treeController.refresh();
            view.showExplorerTree();
            treeController.setOnFilterError(view::setFilterError);
            view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                    ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, taskMonitor()));
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
        treeController.refresh();
    }

    /// Fetches the tree again.
    public void refresh() {
        treeController.refresh();
    }

}
