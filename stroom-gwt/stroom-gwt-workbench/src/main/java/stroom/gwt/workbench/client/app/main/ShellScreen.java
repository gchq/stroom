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

import stroom.content.client.component.ContentTabPaneController;
import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.MoveContentTabEvent;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.core.client.event.ShowFullScreenEvent;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.event.client.StaticEventBus;
import stroom.explorer.client.component.ExplorerController;
import stroom.gwt.workbench.client.app.gin.shell.ShellScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.main.client.component.Main;
import stroom.main.client.component.MainController;
import stroom.main.client.event.ShowMainEvent;
import stroom.widget.help.client.HelpManager;
import stroom.widget.menu.client.MenuItems;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// Stroom's app shell, put together for the `App/Main/AppShell` stories as Stroom's GIN modules and
/// start-up put it together: the main view ([MainController], with the main menu button), the
/// explorer ([ExplorerController]) and the document tabs ([ContentTabPaneController]) in it, every
/// plugin that adds to the main menu (and binds keyboard shortcuts), the Dictionary, Feed and Folder
/// document plugins, the
/// `DocumentPluginEventManager` (the explorer's and tabs' menus, opening, saving, renaming...) and
/// the dialogs that the listeners would show for their events (Find, Recent Items, New, Copy, Move,
/// Rename, Info, Export, Edit Tags, About).
///
/// [#start(DocRef)] logs in as Stroom's `CurrentUser` and `AppPageController` do once the harness has
/// loaded the UI config and preferences: the splash screen (if enabled), the initial activity
/// chooser (if enabled), then `ShowMainEvent`, which shows the explorer and opens the initial
/// document, if any.
///
/// Stroom's `MainController` adds its keyboard handlers to the page's body, and starts its 30 second
/// refresh timer, until it is disposed (when the story renders again); the story gives it a key
/// handler that passes keys to Stroom's `GlobalKeyHandlerImpl` only until then.
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
            createMain();
            injector.getSplashScreen().show(harness.getHasHandlers(), ok -> {
                if (ok) {
                    injector.getCurrentActivity().showInitialActivityChooser(activity ->
                            ShowMainEvent.fire(harness.getHasHandlers(), initialDocRef));
                }
            });
        });
    }

    /// Opens a document in a full screen view, as Stroom's `AppPageController` does for a URL naming a
    /// document without the `open-doc` action (Stroom's embedded view), instead of showing the
    /// shell.
    ///
    /// @param docRef The document.
    void startFullScreen(final DocRef docRef) {
        harness.addRegistration(harness.getEventBus().addHandler(ShowFullScreenEvent.getType(), event ->
                harness.show(event.getController())));
        harness.afterStartUp(() -> {
            registerPlugins();
            OpenDocumentEvent.fire(harness.getHasHandlers(), docRef, true, true);
        });
    }

    // The main view, with the explorer and the document tabs. Stroom shows it (and the explorer) for
    // ShowMainEvent, once the user has logged in
    private void createMain() {
        final EventBus eventBus = harness.getEventBus();

        // The document tabs, registered for the content events as their listener would be
        final ContentTabPaneController tabPane = injector.getContentTabPaneController();
        harness.addRegistration(eventBus.addHandler(OpenContentTabEvent.getType(), tabPane));
        harness.addRegistration(eventBus.addHandler(CloseContentTabEvent.getType(), tabPane));
        harness.addRegistration(eventBus.addHandler(SelectContentTabEvent.getType(), tabPane));
        harness.addRegistration(eventBus.addHandler(MoveContentTabEvent.getType(), tabPane));
        harness.addRegistration(eventBus.addHandler(RefreshContentTabEvent.getType(), tabPane));

        final ExplorerController explorer = injector.getExplorerController();

        // The main view, with the app's keyboard shortcuts, which owns the tabs and the explorer
        final StoryKeyHandler keyHandler = new StoryKeyHandler(injector.getGlobalKeyHandlerImpl());
        harness.addCleanUp(keyHandler::stop);
        final MainController main = new MainController(eventBus,
                new Main(),
                new MenuItems(),
                harness.getUiConfigCache(),
                keyHandler,
                explorer,
                tabPane);
        harness.own(main);

        // As Stroom shows it for ShowMainEvent, once the user has logged in: MainScreens puts it in the
        // page (here, the story's), then it shows the explorer
        harness.addRegistration(eventBus.addHandler(ShowMainEvent.getType(), event -> {
            harness.show(main);
            main.onShowMain(event);
        }));
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

    // Stroom makes the plugins as eager singletons; getting them from the injector makes them, and
    // they register for their events then, for the injector's life (opening the screen again, as
    // the leak check does, gets the same ones)
    private void registerPlugins() {
        injector.getDocumentPluginEventManager();
        injector.getTabSessionManager();
        // The document dialogs (create, copy, move, rename, info), which the area's GIN module makes
        // eagerly in Stroom (the explorer's and import/export's dialogs are their plugins')
        injector.getDocumentDialogScreens();

        injector.getAboutPlugin();
        injector.getAnnotationBrowsePlugin();
        injector.getAnnotationCreatePlugin();
        injector.getAnnotationCollectionPlugin();
        injector.getAnnotationCommentPlugin();
        injector.getAnnotationLabelPlugin();
        injector.getAnnotationStatusPlugin();
        injector.getCachesPlugin();
        injector.getContentStorePlugin();
        injector.getCredentialsPlugin();
        injector.getManageFsVolumesPlugin();
        injector.getExplorerPlugin();
        injector.getHelpPlugin();
        injector.getDependenciesPlugin();
        injector.getExportConfigPlugin();
        injector.getImportConfigPlugin();
        injector.getManageIndexVolumesPlugin();
        injector.getDatabaseTablesMonitoringPlugin();
        injector.getExecutionSchedulesPlugin();
        injector.getJobsPlugin();
        injector.getNodeGroupsPlugin();
        injector.getNodesPlugin();
        injector.getProcessorProfilesPlugin();
        injector.getManageGlobalPropertiesPlugin();
        injector.getTracesPlugin();
        injector.getUserPreferencesPlugin();
        injector.getResultStorePlugin();
        injector.getContentTemplatePlugin();
        injector.getDataRetentionPlugin();
        injector.getReceiveDataRuleSetPlugin();
        injector.getApiKeysPlugin();
        injector.getAppPermissionsPlugin();
        injector.getDocumentPermissionsPlugin();
        injector.getLogoutPlugin();
        injector.getSigningKeyPlugin();
        injector.getSignOutOtherSessionsPlugin();
        injector.getUserAccessPlugin();
        injector.getUserPlugin();
        injector.getUsersAndGroupsPlugin();
        injector.getUsersPlugin();
        injector.getAccountsPlugin();
        injector.getChangePasswordPlugin();
        injector.getServerTasksPlugin();

        injector.getDictionaryPlugin();
        injector.getFeedPlugin();
        injector.getFolderPlugin();
        injector.getFolderFavouritesPlugin();
        injector.getFolderRootPlugin();
    }

    // --------------------------------------------------------------------------------

    /// Passes keys to Stroom's app-wide key handler until stopped (when the story renders again),
    /// as the main controller's handlers on the page's body are never removed.
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

    /// Creates a value when it is first asked for, as a plugin creates a screen when its
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
