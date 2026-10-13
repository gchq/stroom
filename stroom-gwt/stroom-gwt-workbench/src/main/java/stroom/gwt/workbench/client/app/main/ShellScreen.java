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


package stroom.gwt.workbench.client.app.main;

import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.MoveContentTabEvent;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.core.client.event.ShowFullScreenEvent;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.ShowCopyDocumentDialogEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.document.client.event.ShowMoveDocumentDialogEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.entity.client.presenter.CopyDocumentPresenter;
import stroom.entity.client.presenter.CreateDocumentPresenter;
import stroom.entity.client.presenter.InfoDocumentPresenter;
import stroom.entity.client.presenter.MoveDocumentPresenter;
import stroom.entity.client.presenter.NameDocumentPresenter;
import stroom.event.client.StaticEventBus;
import stroom.explorer.client.event.ShowEditNodeTagsDialogEvent;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.event.ShowFindInContentEvent;
import stroom.explorer.client.event.ShowRecentItemsEvent;
import stroom.explorer.client.event.ShowRemoveNodeTagsDialogEvent;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter;
import stroom.explorer.client.presenter.FindInContentPresenter;
import stroom.explorer.client.presenter.FindPresenter;
import stroom.explorer.client.presenter.NavigationPresenter;
import stroom.explorer.client.presenter.RecentItemsPresenter;
import stroom.gwt.workbench.client.app.gin.shell.ShellScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.importexport.client.event.ExportConfigEvent;
import stroom.importexport.client.presenter.ExportConfigPresenter;
import stroom.main.client.event.ShowMainEvent;
import stroom.main.client.presenter.MainPresenter;
import stroom.widget.help.client.presenter.HelpManager;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HandlerContainerImpl;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// Stroom's app shell, put together for the `App/Main/AppShell` stories as Stroom's GIN modules and
/// start-up put it together: the main view ([MainPresenter], with the main menu button), the
/// explorer ([NavigationPresenter]) in its explorer slot and the document tabs
/// ([ContentTabPanePresenter]) in its content slot, every plugin that adds to the main menu (and
/// binds keyboard shortcuts), the Dictionary, Feed and Folder document plugins, the
/// `DocumentPluginEventManager` (the explorer's and tabs' menus, opening, saving, renaming...) and
/// the dialogs that GWTP's proxies would show for their events (Find, Recent Items, New, Copy, Move,
/// Rename, Info, Export, Edit Tags, About).
///
/// [#start(DocRef)] logs in as Stroom's `CurrentUser` and `CorePresenter` do once the harness has
/// loaded the UI config and preferences: the splash screen (if enabled), the initial activity
/// chooser (if enabled), then `ShowMainEvent`, which shows the explorer and opens the initial
/// document, if any.
///
/// Stroom's `MainPresenter` adds its keyboard handlers to the page's body and never removes them;
/// the story gives it a key handler that passes keys to Stroom's `GlobalKeyHandlerImpl` only until
/// the story renders again. Its 30 second refresh timer is cancelled on re-render.
final class ShellScreen {

    private final ShellScreenGinjector injector;
    private final ScreenHarness harness;

    private ShellScreen(final ShellScreenGinjector injector, final ScreenHarness harness) {
        this.injector = injector;
        this.harness = harness;
        // As Stroom's app does at start-up (eager singletons): form help buttons fire their events
        // on the static event bus, which the help manager shows
        new StaticEventBus(harness.getEventBus());
        new HelpManager(harness.getEventBus());
    }

    /// Creates the shell, with a harness built as the given configurer says (e.g. its user, app
    /// permissions or UI config) and showing Stroom's real alerts.
    ///
    /// @param context    The story's context.
    /// @param fixtures   The replies to the shell's REST requests.
    /// @param configurer Sets the harness's options.
    /// @return The shell.
    static ShellScreen create(final StoryContext context,
                              final RestFixtures fixtures,
                              final Consumer<ScreenHarness.Builder> configurer) {
        final ShellScreenGinjector injector = GWT.create(ShellScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts();
        configurer.accept(builder);
        return new ShellScreen(injector, builder.build());
    }

    /// Logs in once the harness has loaded the UI config and preferences, as Stroom does (see the
    /// class description), then shows the explorer and opens the initial document.
    ///
    /// @param initialDocRef The document to open, as for Stroom's `open-doc` URL action, or null.
    void start(final DocRef initialDocRef) {
        harness.afterStartUp(() -> {
            registerPlugins();
            registerDialogs();
            createMain();
            injector.getSplashPresenter().show(ok -> {
                if (ok) {
                    injector.getCurrentActivity().showInitialActivityChooser(activity ->
                            ShowMainEvent.fire(harness.getHasHandlers(), initialDocRef));
                }
            });
        });
    }

    /// Opens a document in a full screen view, as Stroom's `CorePresenter` does for a URL naming a
    /// document without the `open-doc` action (Stroom's embedded view), instead of showing the
    /// shell.
    ///
    /// @param docRef The document.
    void startFullScreen(final DocRef docRef) {
        harness.addRegistration(harness.getEventBus().addHandler(ShowFullScreenEvent.getType(), event ->
                harness.addContent(event.getPresenterWidget())));
        harness.afterStartUp(() -> {
            registerPlugins();
            registerDialogs();
            OpenDocumentEvent.fire(harness.getHasHandlers(), docRef, true, true);
        });
    }

    // The main view, with the explorer and the document tabs in its slots. Stroom shows it (and the
    // explorer) for ShowMainEvent, once the user has logged in
    private void createMain() {
        final EventBus eventBus = harness.getEventBus();

        // The document tabs, registered for the content events as their GWTP proxy would be
        final ContentTabPanePresenter tabPane = injector.getContentTabPanePresenter();
        harness.addRegistration(eventBus.addHandler(OpenContentTabEvent.getType(), tabPane));
        harness.addRegistration(eventBus.addHandler(CloseContentTabEvent.getType(), tabPane));
        harness.addRegistration(eventBus.addHandler(SelectContentTabEvent.getType(), tabPane));
        harness.addRegistration(eventBus.addHandler(MoveContentTabEvent.getType(), tabPane));
        harness.addRegistration(eventBus.addHandler(RefreshContentTabEvent.getType(), tabPane));
        harness.unbindOnCleanUp(tabPane);

        // The main view, with the app's keyboard shortcuts
        final StoryKeyHandler keyHandler = new StoryKeyHandler(injector.getGlobalKeyHandlerImpl());
        harness.addCleanUp(keyHandler::stop);
        final MainPresenter main = new MainPresenter(eventBus,
                injector.getMainView(),
                null,
                new MenuItems(),
                harness.getUiConfigCache(),
                keyHandler);
        main.bind();
        harness.unbindOnCleanUp(main);
        harness.addCleanUp(() -> cancelRefreshTimer(main));

        // As Stroom shows them for ShowMainEvent, once the user has logged in: the main view (made
        // visible as GWTP's root presenter would make it) with the tab pane in its content slot,
        // then the explorer, which shows itself (NavigationPresenter.onShowMain, registered as its
        // GWTP proxy would be), and is put in the main view's explorer slot as the main view's
        // proxy would do for the explorer's RevealContentEvent
        final NavigationPresenter navigation = injector.getNavigationPresenter();
        harness.unbindOnCleanUp(navigation);
        harness.addRegistration(eventBus.addHandler(ShowMainEvent.getType(), event -> {
            main.setInSlot(MainPresenter.CONTENT, tabPane);
            harness.addContent(main);
            reveal(main);
        }));
        harness.addRegistration(eventBus.addHandler(ShowMainEvent.getType(), navigation));
        harness.addRegistration(eventBus.addHandler(ShowMainEvent.getType(), event ->
                main.setInSlot(MainPresenter.EXPLORER, navigation)));
    }

    /// @return The shell's injector.
    ShellScreenGinjector getInjector() {
        return injector;
    }

    /// @return The harness.
    ScreenHarness getHarness() {
        return harness;
    }

    /// @return The story's widget.
    Widget asWidget() {
        return harness.asWidget();
    }

    // Stroom binds the plugins as eager singletons; getting them from the injector creates and
    // registers them
    private void registerPlugins() {
        unbindOnCleanUp(injector.getDocumentPluginEventManager());
        unbindOnCleanUp(injector.getTabSessionManager());

        unbindOnCleanUp(injector.getAboutPlugin());
        unbindOnCleanUp(injector.getAnnotationBrowsePlugin());
        unbindOnCleanUp(injector.getAnnotationCreatePlugin());
        unbindOnCleanUp(injector.getAnnotationCollectionPlugin());
        unbindOnCleanUp(injector.getAnnotationCommentPlugin());
        unbindOnCleanUp(injector.getAnnotationLabelPlugin());
        unbindOnCleanUp(injector.getAnnotationStatusPlugin());
        unbindOnCleanUp(injector.getCacheMonitoringPlugin());
        unbindOnCleanUp(injector.getContentStorePlugin());
        unbindOnCleanUp(injector.getCredentialsPlugin());
        unbindOnCleanUp(injector.getManageFsVolumesPlugin());
        unbindOnCleanUp(injector.getNavigationPlugin());
        unbindOnCleanUp(injector.getHelpPlugin());
        unbindOnCleanUp(injector.getDependenciesPlugin());
        unbindOnCleanUp(injector.getExportConfigPlugin());
        unbindOnCleanUp(injector.getImportConfigPlugin());
        unbindOnCleanUp(injector.getManageIndexVolumesPlugin());
        unbindOnCleanUp(injector.getDatabaseTablesMonitoringPlugin());
        unbindOnCleanUp(injector.getExecutionScheduleManagerPlugin());
        unbindOnCleanUp(injector.getJobListPlugin());
        unbindOnCleanUp(injector.getNodeGroupsPlugin());
        unbindOnCleanUp(injector.getNodeMonitoringPlugin());
        unbindOnCleanUp(injector.getProcessorProfilePlugin());
        unbindOnCleanUp(injector.getManageGlobalPropertiesPlugin());
        unbindOnCleanUp(injector.getTracesPlugin());
        unbindOnCleanUp(injector.getUserPreferencesPlugin());
        unbindOnCleanUp(injector.getResultStorePlugin());
        unbindOnCleanUp(injector.getContentTemplatePlugin());
        unbindOnCleanUp(injector.getDataRetentionPlugin());
        unbindOnCleanUp(injector.getReceiveDataRuleSetPlugin());
        unbindOnCleanUp(injector.getApiKeysPlugin());
        unbindOnCleanUp(injector.getAppPermissionsPlugin());
        unbindOnCleanUp(injector.getDocumentPermissionsPlugin());
        unbindOnCleanUp(injector.getLogoutPlugin());
        unbindOnCleanUp(injector.getSigningKeyPlugin());
        unbindOnCleanUp(injector.getSignOutOtherSessionsPlugin());
        unbindOnCleanUp(injector.getUserAccessPlugin());
        unbindOnCleanUp(injector.getUserPermissionsReportPlugin());
        unbindOnCleanUp(injector.getUserPlugin());
        unbindOnCleanUp(injector.getUsersAndGroupsPlugin());
        unbindOnCleanUp(injector.getUsersPlugin());
        unbindOnCleanUp(injector.getAccountsPlugin());
        unbindOnCleanUp(injector.getChangePasswordPlugin());
        unbindOnCleanUp(injector.getTaskManagerPlugin());

        unbindOnCleanUp(injector.getDictionaryPlugin());
        unbindOnCleanUp(injector.getFeedPlugin());
        unbindOnCleanUp(injector.getFolderPlugin());
        unbindOnCleanUp(injector.getFolderFavouritesPlugin());
        unbindOnCleanUp(injector.getFolderRootPlugin());
    }

    private void unbindOnCleanUp(final HandlerContainerImpl plugin) {
        harness.addCleanUp(plugin::unbind);
    }

    // The dialogs that Stroom's GWTP proxies create and show for their events
    private void registerDialogs() {
        final Lazy<FindPresenter> findPresenter = new Lazy<>(injector::getFindPresenter);
        final Lazy<FindInContentPresenter> findInContentPresenter = new Lazy<>(injector::getFindInContentPresenter);
        final Lazy<RecentItemsPresenter> recentItemsPresenter = new Lazy<>(injector::getRecentItemsPresenter);
        final Lazy<CreateDocumentPresenter> createDocumentPresenter = new Lazy<>(injector::getCreateDocumentPresenter);
        final Lazy<CopyDocumentPresenter> copyDocumentPresenter = new Lazy<>(injector::getCopyDocumentPresenter);
        final Lazy<MoveDocumentPresenter> moveDocumentPresenter = new Lazy<>(injector::getMoveDocumentPresenter);
        final Lazy<NameDocumentPresenter> nameDocumentPresenter = new Lazy<>(injector::getNameDocumentPresenter);
        final Lazy<InfoDocumentPresenter> infoDocumentPresenter = new Lazy<>(injector::getInfoDocumentPresenter);
        final Lazy<ExportConfigPresenter> exportConfigPresenter = new Lazy<>(injector::getExportConfigPresenter);
        final Lazy<ExplorerNodeEditTagsPresenter> explorerNodeEditTagsPresenter = new Lazy<>(
                injector::getExplorerNodeEditTagsPresenter);
        final Lazy<ExplorerNodeRemoveTagsPresenter> explorerNodeRemoveTagsPresenter = new Lazy<>(
                injector::getExplorerNodeRemoveTagsPresenter);
        final EventBus eventBus = harness.getEventBus();
        harness.addRegistration(eventBus.addHandler(ShowFindEvent.getType(), event ->
                findPresenter.get().onShow(event)));
        harness.addRegistration(eventBus.addHandler(ShowFindInContentEvent.getType(), event ->
                findInContentPresenter.get().onShow(event)));
        harness.addRegistration(eventBus.addHandler(ShowRecentItemsEvent.getType(), event ->
                recentItemsPresenter.get().onShowRecentItems(event)));
        harness.addRegistration(eventBus.addHandler(ShowCreateDocumentDialogEvent.getType(), event ->
                createDocumentPresenter.get().onCreate(event)));
        harness.addRegistration(eventBus.addHandler(ShowCopyDocumentDialogEvent.getType(), event ->
                copyDocumentPresenter.get().onCopy(event)));
        harness.addRegistration(eventBus.addHandler(ShowMoveDocumentDialogEvent.getType(), event ->
                moveDocumentPresenter.get().onMove(event)));
        harness.addRegistration(eventBus.addHandler(ShowRenameDocumentDialogEvent.getType(), event ->
                nameDocumentPresenter.get().onRename(event)));
        harness.addRegistration(eventBus.addHandler(ShowInfoDocumentDialogEvent.getType(), event ->
                infoDocumentPresenter.get().onCreate(event)));
        harness.addRegistration(eventBus.addHandler(ExportConfigEvent.getType(), event ->
                exportConfigPresenter.get().onExport(event)));
        harness.addRegistration(eventBus.addHandler(ShowEditNodeTagsDialogEvent.getType(), event ->
                explorerNodeEditTagsPresenter.get().onCreate(event)));
        harness.addRegistration(eventBus.addHandler(ShowRemoveNodeTagsDialogEvent.getType(), event ->
                explorerNodeRemoveTagsPresenter.get().onCreate(event)));
    }

    // Makes a presenter visible (calling its onReveal and its children's), as GWTP does when it
    // reveals a presenter in its parent's slot (here the root, which the story has no presenter for).
    // A visible tab pane keeps its tabs when it is asked to reveal itself for each new tab
    private static native void reveal(MainPresenter presenter) /*-{
        presenter.@com.gwtplatform.mvp.client.PresenterWidget::internalReveal()();
    }-*/;

    // MainPresenter's refresh timer (it fires RefreshCurrentContentTabEvent every 30 seconds) is
    // never cancelled by Stroom, which has one main presenter for the page's life
    private static native void cancelRefreshTimer(MainPresenter main) /*-{
        main.@stroom.main.client.presenter.MainPresenter::refreshTimer.@com.google.gwt.user.client.Timer::cancel()();
    }-*/;

    // --------------------------------------------------------------------------------


    /// Passes keys to Stroom's app-wide key handler until stopped (when the story renders again),
    /// as the main presenter's handlers on the page's body are never removed.
    private static final class StoryKeyHandler implements GlobalKeyHandler {

        private final GlobalKeyHandler delegate;
        private boolean stopped;

        private StoryKeyHandler(final GlobalKeyHandler delegate) {
            this.delegate = delegate;
        }

        private void stop() {
            stopped = true;
        }

        @Override
        public void onKeyDown(final KeyDownEvent event) {
            if (!stopped) {
                delegate.onKeyDown(event);
            }
        }

        @Override
        public void onKeyUp(final KeyUpEvent event) {
            if (!stopped) {
                delegate.onKeyUp(event);
            }
        }
    }

    // --------------------------------------------------------------------------------


    /// Creates a value when it is first asked for, as a GWTP proxy creates its presenter when its
    /// event first fires, and gives the same one after that.
    private static final class Lazy<T> implements Supplier<T> {

        private final Supplier<T> supplier;
        private T value;

        private Lazy(final Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (value == null) {
                value = supplier.get();
            }
            return value;
        }
    }
}
