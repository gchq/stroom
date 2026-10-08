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

import stroom.docref.DocRef;
import stroom.document.client.DocumentPluginRegistry;
import stroom.document.client.DocumentTabData;
import stroom.gwt.workbench.client.app.editors.DocEditors;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;

import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Stories matching `App/Main/AppShell` in the React Storybook: Stroom's app shell put together by
/// [ShellScreen] from Stroom's real presenters and plugins: the main view (its main menu), the
/// explorer (`NavigationPresenter`: the tree, its toolbar and the current activity), the document
/// tabs (`ContentTabPanePresenter`), the explorer's and tabs' menus and document actions
/// (`DocumentPluginEventManager`), keyboard shortcuts (`GlobalKeyHandlerImpl`), and the Dictionary,
/// Feed and Folder editors.
///
/// React's seams become Stroom's REST endpoints:
///
/// | React seam | Stroom endpoint |
/// |---|---|
/// | `explorerNodes`, `explorerLoadNodes` | `POST /explorer/v2/fetchExplorerNodes` ([ExplorerFixture]) |
/// | `appApi` | the start-up fixtures (session, UI config, preferences, app permissions) |
/// | `explorerCrud.fetchDocumentTypes` | `GET /explorer/v2/fetchDocumentTypes` |
/// | `explorerCrud.fetchPermissions` | `POST /explorer/v2/fetchExplorerPermissions` |
/// | `explorerCrud` create, rename, copy, move, remove, info | `/explorer/v2/` + `create`, `rename`, ... |
/// | `explorerCrud.fetchDeleteConfirmation` | `POST /explorer/v2/fetchDeleteConfirmation` |
/// | `decorate`, `deepLink` | `POST /explorer/v2/decorate`; the deep link is `ShowMainEvent`'s document |
/// | `dictionaryApi`, `wordListApi` | `GET`/`PUT /dictionary/v1/{uuid}`, `GET /wordList/v1/{uuid}` |
/// | `feedApi`, `feedOptionsApi` | `/feed/v1/...`, `GET /meta/v1/getTypes`, `POST /fsVolume/volumeGroup/v2/find` |
/// | `metaApi`, `processorTaskApi` | `POST /meta/v1/find`, `/processorTask/v1/...` |
/// | `docPermissionApi` | the Permissions tab's `/permission/doc/v1/...` |
/// | `activityApi` | `/activity/v1/...` (`setCurrent` is checked on the request spy) |
/// | `content.exportContent`, `downloadResource` | `POST /content/v1/export`, the download spy |
/// | `appApi.terminateOtherSessions` | `POST /session/v1/terminateOther` |
public final class AppShellStories {

    /// The name of the spy recording the registry's answers in `LocateOnlyForExplorerTabs`.
    static final String LOCATE_SPY = "getExplorerDocRef";

    // Differs from React: the explorer panel is NavigationViewImpl's '.navigation' (React's class)
    private static final String NAVIGATION = ".navigation";
    // Differs from React: GWT's document tabs have no 'tab' role; their labels are '.curveTab-text'
    private static final String TAB_LABEL = ".curveTab-text";
    // Differs from React: menu items have no 'menuitem' role or aria-disabled; a disabled item has
    // the 'menuItem-disabled' class
    private static final String MENU_ITEM = ".menuItem-outer";
    private static final String MENU_ITEM_DISABLED = "menuItem-disabled";
    private static final String MENU_SHORTCUT = ".menuItem-shortcut";
    // Differs from React: the main menu button is the explorer's 'Main Menu' (React's 'Application
    // menu')
    private static final String MAIN_MENU = "Main Menu";

    private static final String DICTIONARY_PATH = "/dictionary/v1/";
    private static final String CREATE_PATH = "/explorer/v2/create";
    private static final String RENAME_PATH = "/explorer/v2/rename";
    private static final String DELETE_PATH = "/explorer/v2/delete";
    private static final String COPY_PATH = "/explorer/v2/copy";
    private static final String MOVE_PATH = "/explorer/v2/move";
    private static final String DELETE_CONFIRMATION_PATH = "/explorer/v2/fetchDeleteConfirmation";
    private static final String PERMISSIONS_PATH = "/explorer/v2/fetchExplorerPermissions";
    private static final String TERMINATE_OTHER_PATH = "/session/v1/terminateOther";
    private static final String EXPORT_PATH = "/content/v1/export";
    private static final String ACKNOWLEDGE_SPLASH_PATH = ActivityFixtures.PATH + "/acknowledgeSplash";

    private static final String EMPTY_PAGE = """
            {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}""";

    // React's dictionaryFixture: fixed words and two imports (so the lifecycle story can edit by
    // removing them)
    private static final String COUNTRIES = """
            {"type": "Dictionary", "uuid": "dict-countries", "name": "Countries",
              "description": "ISO country names", "data": "England\\nScotland\\nWales",
              "imports": [{"type": "Dictionary", "uuid": "dict-alpha", "name": "Alpha"},
                          {"type": "Dictionary", "uuid": "dict-beta", "name": "Beta"}]}""";
    // React's wordListFixture
    private static final String WORDS = """
            {"wordList": [{"word": "sample", "sourceUuid": "dict-countries"}], "sourceUuidToDocRefMap": {}}""";

    // React's feedFixture
    private static final String FEED = """
            {"type": "Feed", "uuid": "feed-events", "name": "EVENTS", "status": "RECEIVE",
              "encoding": "UTF-8", "dataFormat": "JSON", "streamType": "Raw Events", "reference": false}""";
    // React's metaFixture
    private static final String STREAMS = """
            {"values": [{"meta": {"id": 101, "feedName": "EVENTS", "typeName": "Raw Events",
                "status": "UNLOCKED", "createMs": 1700000000000}, "attributes": {}}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";
    // React's crudFixture.info
    private static final String INFO = """
            {"explorerNode": {"type": "Dictionary", "uuid": "dict-countries", "name": "Countries",
                "tags": ["stroom"]},
              "auditEntries": {"values": [
                  {"time": 1700000000000, "user": {"displayName": "admin"}, "action": "CREATE"},
                  {"time": 1700100000000, "user": {"displayName": "admin"}, "action": "UPDATE"}],
                "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}}""";
    // React's typedCrud and crudFixture document types
    private static final String FEED_TYPE = """
            {"group": "DATA_PROCESSING", "type": "Feed", "displayType": "Feed", "icon": "DOCUMENT_FEED"}""";
    private static final String DICTIONARY_TYPE = """
            {"group": "STRUCTURE", "type": "Dictionary", "displayType": "Dictionary", "icon": "DOCUMENT_DICTIONARY"}""";
    private static final String FEED_AND_DICTIONARY_TYPES = documentTypes(FEED_TYPE + ", " + DICTIONARY_TYPE);
    private static final String DICTIONARY_TYPES = documentTypes(DICTIONARY_TYPE);
    // React's DeleteWithDependantsWarns fixture
    private static final String DELETE_WITH_DEPENDANTS = """
            {"totalChildCount": 2, "childTypeCounts": {"Dictionary": 2},
              "childItems": [{"type": "Dictionary", "uuid": "d1", "name": "Contained One"},
                             {"type": "Dictionary", "uuid": "d2", "name": "Contained Two"}],
              "visibleDependants": [{"type": "Pipeline", "uuid": "p1", "name": "Dependent Pipeline"}],
              "hasHiddenDependants": true}""";
    private static final String SPLASH = """
            "splash": {"enabled": true, "title": "Terms of Use",
              "body": "<p id=\\"terms\\">Accept to continue.</p>", "version": "v1"}""";

    private static final String ACTIVITY_TITLE = "Choose Activity";
    private static final String[] ACTIVITIES = {
            ActivityFixtures.activity(1,
                    ActivityFixtures.prop("ref", "Reference", "INV-1001", true),
                    ActivityFixtures.prop("desc", "Description", "Phishing campaign sweep", false)),
            ActivityFixtures.activity(2,
                    ActivityFixtures.prop("ref", "Reference", "INV-1002", true),
                    ActivityFixtures.prop("desc", "Description", "Malware triage", false))};

    private AppShellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/AppShell", AppShellStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The full shell over the rich fixture tree (Explorer left, empty content right)
                .story("Default", context -> render(context, TreeFixtures.fixtureTree()))
                // The explorer's Filter Types button opens the type filter (the document types);
                // turning a type off restricts the tree and lights the button
                .story("ExplorerTypeFilter", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(StartupFixtures.DOCUMENT_TYPES_PATH,
                                RestReply.json(FEED_AND_DICTIONARY_TYPES)),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play nav = navTree(play);
                    final Query filter = nav.getByRole("button", "Filter Types");
                    // Differs from React: the toggle button shows its state with the 'on' class,
                    // not aria-pressed
                    play.expect(filter).not().toHaveClass("on");
                    play.click(filter);
                    final Play screen = play.screen();
                    screen.findByText("All / None");
                    play.expect(screen.getByText("Feed")).toBeInTheDocument();
                    play.expect(screen.getByText("Dictionary")).toBeInTheDocument();
                    play.click(screen.getByText("Feed"));
                    play.waitFor(() -> play.expect(filter).toHaveClass("on"));
                    expectNoProblems(play);
                })
                // The Toggle Alerts button shows only when dependency warnings are enabled
                .story("ExplorerToggleAlerts", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> {
                        },
                        builder -> builder.startup(startup -> startup.extendedUiConfig(
                                "{\"uiConfig\": " + StartupFixtures.DEFAULT_UI_CONFIG
                                + ", \"dependencyWarningsEnabled\": true}")),
                        null))
                .withPlay(play -> {
                    final Play nav = navTree(play);
                    final Query alerts = nav.getByRole("button", "Toggle Alerts");
                    play.expect(alerts).not().toHaveClass("on");
                    play.click(alerts);
                    play.waitFor(() -> play.expect(alerts).toHaveClass("on"));
                    expectNoProblems(play);
                })
                // A document opened on its own, without the explorer or toolbar
                .story("Embedded", AppShellStories::renderEmbedded)
                .withPlay(play -> {
                    // Differs from React: Stroom shows a document without the shell for a URL that
                    // names it without the open-doc action (CorePresenter); with open-doc (React's
                    // deep link) it shows the whole shell, embedded or not. So the story opens the
                    // document as Stroom's embedded view does: its editor, without the explorer or
                    // the Ask Stroom AI button
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.waitFor(() -> play.expect(play.querySelector(NAVIGATION)).toBeNull());
                    play.expect(play.screen().queryByTitle("Ask Stroom AI")).toBeNull();
                    expectNoProblems(play);
                })
                // Double-tapping Shift opens the Find dialog
                .story("FindShortcut", context -> render(context, TreeFixtures.fixtureTree()))
                .withPlay(play -> {
                    play.findByRole("button", TextMatch.regex("expand all", "i"));
                    play.keyboard("{Shift}{Shift}");
                    // Differs from React: GWT's Find dialog's quick filter has no label; the
                    // dialog's caption is 'Find'
                    play.waitFor(() -> play.expect(play.screen().getByText("Find", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    expectNoProblems(play);
                })
                // The main menu's groups, as the user's permissions allow
                .story("MainMenu", context -> render(context, TreeFixtures.fixtureTree(), routes -> {
                }, builder -> builder.appPermissions(AppPermission.EXPORT_CONFIGURATION,
                        AppPermission.MANAGE_TASKS_PERMISSION), null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "User")).toBeInTheDocument());
                    play.expect(menuItem(screen, "Tools")).toBeInTheDocument();
                    play.expect(menuItem(screen, "Monitoring")).toBeInTheDocument();
                    play.expect(menuItem(screen, "Help")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // The menu shows the keyboard shortcuts, and 'g' then 'u' opens the preferences
                .story("GotoShortcut", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Dictionaries");
                    play.click(play.getByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "User")).toBeInTheDocument());
                    // Differs from React: GWT shows a group's items (with their shortcuts) only
                    // while the group is hovered, so each group is hovered in turn
                    play.hover(menuItem(screen, "User"));
                    play.waitFor(() -> play.expect(shortcuts(play)).toContain("gu"));
                    play.hover(menuItem(screen, "Tools"));
                    play.waitFor(() -> play.expect(shortcuts(play)).toContain("gd"));
                    play.hover(menuItem(screen, "Navigation"));
                    play.waitFor(() -> play.expect(shortcuts(play)).toContain("Alt+Shift+f"));
                    play.expect(shortcuts(play)).toContain("Ctrl+Shift+f");
                    play.expect(shortcuts(play)).toContain("Ctrl+e");
                    play.expect(shortcuts(play)).toContain("Alt+l");
                    play.keyboard("{Escape}");
                    // Differs from React: GWT's key sequences complete on the keys' release, so
                    // the keys are pressed and released rather than only pressed
                    play.keyboard("gu");
                    play.waitFor(() -> play.expect(screen.getByText("User Preferences", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    expectNoProblems(play);
                })
                // The maintenance banner above the shell
                .story("Banner", context -> render(context, TreeFixtures.fixtureTree(), routes -> {
                }, builder -> builder.uiConfig(QueryFixtures.uiConfigWith("\"maintenanceMessage\": "
                        + "\"System maintenance scheduled for 22:00 UTC — expect brief downtime.\"")), null))
                .withPlay(play -> {
                    // Differs from React: GWT's banner has no 'alert' role
                    play.waitFor(() -> play.expect(play.querySelector(".mainViewImpl-banner"))
                            .toHaveTextContent(TextMatch.containingIgnoreCase("system maintenance")));
                    expectNoProblems(play);
                })
                // The current activity below the tree opens the chooser; choosing one updates it
                .story("ActivityChooser", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(ActivityFixtures.PATH,
                                RestReply.json(ActivityFixtures.page(ACTIVITIES))),
                        builder -> builder.uiConfig(ActivityFixtures.uiConfig(ACTIVITY_TITLE)), null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Differs from React: GWT's summary is a button with no title
                    final Query summary = play.findByRole("button", TextMatch.startingWith("Current Activity"));
                    play.expect(play.within(summary).getByText("none")).toBeInTheDocument();
                    play.click(play.within(summary).getByText("Current Activity"));
                    screen.findByText(ACTIVITY_TITLE, StroomDom.DIALOG_TITLE);
                    play.click(screen.findByText(TextMatch.containing("INV-1002")));
                    play.click(screen.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(summary).toHaveTextContent(TextMatch.containing("INV-1002")));
                    // The chosen activity was made current (React's ACTIVITY_STATE)
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(ActivityFixtures.CURRENT_PATH)
                                    .withJsonBodyContaining("{\"id\": 2}")
                                    .toSpyMatcher());
                    expectNoProblems(play);
                })
                // With chooseOnStartup the chooser opens at start-up
                .story("ActivityChoosesOnStartup", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(ActivityFixtures.PATH,
                                RestReply.json(ActivityFixtures.page(ACTIVITIES))),
                        builder -> builder.uiConfig(ActivityFixtures.uiConfig(ACTIVITY_TITLE)
                                .replace("\"chooseOnStartup\": false", "\"chooseOnStartup\": true")), null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Differs from React: Stroom asks for the activity before it shows the shell, so
                    // the explorer (and its current activity summary) isn't shown yet
                    play.waitFor(() -> play.expect(screen.getByText(ACTIVITY_TITLE, StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    play.expect(screen.findByText(TextMatch.containing("INV-1001"))).toBeInTheDocument();
                    play.expect(play.queryByText("Dictionaries")).toBeNull();
                    expectNoProblems(play);
                })
                // The splash screen's terms must be accepted before the app is shown
                .story("SplashGate", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.post(ACKNOWLEDGE_SPLASH_PATH, RestReply.json("true")),
                        builder -> builder.uiConfig(QueryFixtures.uiConfigWith(SPLASH)), null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Terms of Use")).toBeInTheDocument());
                    play.expect(screen.getByText("Accept to continue.")).toBeInTheDocument();
                    play.expect(play.queryByText("Dictionaries")).toBeNull();
                    // Reject warns
                    play.click(screen.getByRole("button", TextMatch.regex("reject", "i")));
                    play.waitFor(() -> play.expect(screen.getByText("You must accept the terms to use this system"))
                            .toBeInTheDocument());
                    play.click(screen.getByRole("button", TextMatch.regex("close|ok", "i")));
                    // Differs from React: once the warning is closed, Stroom closes the splash screen
                    // and never shows the app (SplashPresenter hides it and passes the rejection on),
                    // so the terms can't then be accepted in the same story: nothing was acknowledged
                    play.waitFor(() -> play.expect(screen.queryByText("Accept to continue.")).toBeNull());
                    play.expect(play.queryByText("Dictionaries")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(ACKNOWLEDGE_SPLASH_PATH).toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            "WARN: You must accept the terms to use this system");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Double-clicking a document opens its editor in a tab; a folder opens too
                .story("OpensDocument", context -> render(context, AppShellFixtures.dictTree(), routes -> {
                }, builder -> builder.appPermissions(AppPermission.VIEW_DATA_PERMISSION), null))
                .withPlay(play -> {
                    final Play nav = navTree(play);
                    // Differs from React: GWT's empty tab pane shows nothing (React's 'No document
                    // open'), so the play checks there are no tabs
                    play.expect(tabs(play)).toHaveLength(0);
                    play.dblClick(nav.getByText("Countries"));
                    play.expect(play.findByText("Words", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    play.expect(tabs(play)).toHaveLength(1);
                    // A folder opens the same way (FolderPlugin), with its Data tab.
                    // Differs from React: GWT's Data tab lists the folder's streams (no 'Select a
                    // stream to preview' prompt), so the play checks the folder's tab and its stream
                    // (React's metaFixture)
                    play.dblClick(nav.getByText("Dictionaries"));
                    play.waitFor(5000, () -> play.expect(tabs(play)).toHaveLength(2));
                    play.expect(play.getByText("Dictionaries", TAB_LABEL)).toBeInTheDocument();
                    play.expect(play.findByText("Data", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    play.expect(play.findByText("Raw Events")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Opening a Feed shows the Feed editor
                .story("OpensFeed", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    play.dblClick(navTree(play).getByText("EVENTS"));
                    play.expect(play.findByText("Settings", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    play.expect(play.getByText("Data", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    play.expect(play.getByText("Permissions", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    play.click(play.getByText("Settings", StroomDom.LINK_TAB_LABEL));
                    play.expect(play.findByText("Volume Group", "label")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Load, edit (dirty), save, and close with the unsaved changes confirmation
                .story("DictionaryLifecycle", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(navTree(play).getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    // Freshly loaded: Save is disabled
                    play.waitFor(() -> play.expect(play.getByTitle("Save")).toHaveAttribute("aria-disabled", "true"));
                    // Remove an import (confirmed), which makes the document dirty
                    play.click(play.getByText("Imports", StroomDom.LINK_TAB_LABEL));
                    removeImport(play, "Alpha");
                    play.waitFor(() -> play.expect(play.getByTitle("Save")).not().toHaveAttribute("aria-disabled"));
                    // Save: the server echoes, so the document is clean again
                    play.click(play.getByTitle("Save"));
                    play.waitFor(() -> play.expect(play.getByTitle("Save")).toHaveAttribute("aria-disabled", "true"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(DICTIONARY_PATH + "dict-countries")
                                    .withJsonBodyContaining("{\"imports\": [{\"name\": \"Beta\"}]}")
                                    .toSpyMatcher());
                    // Edit again, then close: the unsaved changes are confirmed
                    removeImport(play, "Beta");
                    play.waitFor(() -> play.expect(play.getByTitle("Save")).not().toHaveAttribute("aria-disabled"));
                    // Differs from React: the tab's close icon has no title
                    play.click(play.within(play.getByText(TextMatch.containing("Countries"), TAB_LABEL)
                                    .closest(".curveTab"))
                            .querySelector(".curveTab-close"));
                    play.expect(screen.findByText(TextMatch.containingIgnoreCase("unsaved changes")))
                            .toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(tabs(play)).toHaveLength(0));
                    expectNoProblems(play);
                })
                // Save As creates a new document from the open one and the tab shows the new one
                .story("SaveAsDocument", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(DICTIONARY_PATH + "new-doc", RestReply.json(newDoc("Countries Copy"))),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(navTree(play).getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.waitFor(() -> play.expect(tabs(play)).toHaveLength(1));
                    play.click(play.getByTitle("Save As"));
                    screen.findByText("Save 'Countries' as");
                    final Query name = screen.getByLabelText("Name");
                    play.waitFor(() -> play.expect(name).toHaveValue("Countries"));
                    play.clear(name);
                    play.type(name, "Countries Copy");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    // The new document is created, and the editor's document written to it
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CREATE_PATH)
                                    .withJsonBodyContaining("{\"docName\": \"Countries Copy\"}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(DICTIONARY_PATH + "new-doc").toSpyMatcher()));
                    // The tab is re-keyed in place: still one tab, labelled with the new document's
                    // name (it once kept the old label, as DocTabPresenter.onRead didn't refresh the
                    // tab when the name changed)
                    play.expect(tabs(play)).toHaveLength(1);
                    play.waitFor(() -> play.expect(tabs(play).nth(0)).toHaveTextContent("Countries Copy"));
                    expectNoProblems(play);
                })
                // A node's context menu (no 'Open': a document opens on a double click)
                .story("ContextMenu", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    rightClick(play, nav.getByText("Countries"));
                    play.expect(screen.findByText("Copy As", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    play.expect(screen.getByText("Add to Favourites", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    play.expect(screen.queryByText("Open", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    play.keyboard("{Escape}");
                    play.dblClick(nav.getByText("Countries"));
                    play.expect(play.findByText("Words", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // The System root isn't a document: Copy, Move, Rename and Delete are disabled
                .story("ContextMenuOnSystemRoot", context -> render(context, AppShellFixtures.rootedTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play, "System").getByText("System"));
                    play.expect(screen.findByText("Copy As", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    for (final String name : List.of("Copy", "Move", "Rename", "Delete")) {
                        play.waitFor(() -> play.expect(menuItem(screen, name)).toHaveClass(MENU_ITEM_DISABLED));
                    }
                    play.expect(menuItem(screen, "Permissions")).not().toHaveClass(MENU_ITEM_DISABLED);
                    expectNoProblems(play);
                })
                // A tab's context menu, with positional enablement on the first of two tabs
                .story("TabContextMenu", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    play.dblClick(nav.getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.dblClick(nav.getByText("Colours"));
                    play.waitFor(5000, () -> play.expect(tabs(play)).toHaveLength(2));
                    // Differs from React: the menu is shown on a secondary button's mouseup on a tab
                    play.fireEvent().mouseUp(play.getByText("Countries", TAB_LABEL), EventInit.create().button(2));
                    play.waitFor(() -> play.expect(menuItem(screen, "Close Others")).toBeInTheDocument());
                    for (final String name : List.of("Close", "Close Others", "Close Saved", "Close All",
                            "Close Tabs to the Left", "Close Tabs to the Right", "Save", "Save All", "Move First",
                            "Move Last", "Locate in Explorer", "Add to Favourites")) {
                        play.expect(menuItem(screen, name)).toBeInTheDocument();
                    }
                    for (final String accelerator : List.of("Alt+w", "Alt+Shift+w", "Ctrl+s", "Ctrl+Shift+s",
                            "Alt+l")) {
                        play.expect(shortcuts(play)).toContain(accelerator);
                    }
                    play.expect(menuItem(screen, "Close Tabs to the Left")).toHaveClass(MENU_ITEM_DISABLED);
                    play.expect(menuItem(screen, "Close Tabs to the Right")).not().toHaveClass(MENU_ITEM_DISABLED);
                    play.expect(menuItem(screen, "Move First")).toHaveClass(MENU_ITEM_DISABLED);
                    play.expect(menuItem(screen, "Move Last")).not().toHaveClass(MENU_ITEM_DISABLED);
                    play.expect(menuItem(screen, "Save")).toHaveClass(MENU_ITEM_DISABLED);
                    play.expect(menuItem(screen, "Save All")).toHaveClass(MENU_ITEM_DISABLED);
                    play.click(menuItem(screen, "Close Others"));
                    play.waitFor(() -> play.expect(tabs(play)).toHaveLength(1));
                    play.expect(tabs(play).nth(0)).toHaveTextContent(TextMatch.containing("Countries"));
                    expectNoProblems(play);
                })
                // No menu at all on the Favourites root
                .story("ContextMenuOnFavouritesRoot", context -> render(context, AppShellFixtures.rootedTree()))
                .withPlay(play -> {
                    rightClick(play, navTree(play, "System").getByText("Favourites"));
                    play.sleep(250);
                    play.expect(play.screen().queryByText("Copy As", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    expectNoProblems(play);
                })
                // Info shows the document's identity, audit history and tags
                .story("InfoDialog", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.click(screen.findByText("Info", StroomDom.MENU_ITEM_TEXT));
                    // Differs from React: GWT bolds the key and appends the ': ' after it
                    play.waitFor(() -> play.expect(screen.getByText("UUID")).toBeInTheDocument());
                    play.expect(screen.getByText("Type")).toBeInTheDocument();
                    play.expect(screen.getByText("Tags")).toBeInTheDocument();
                    play.expect(screen.getByText("Audit Info")).toBeInTheDocument();
                    play.expect(screen.getByText("Created")).toBeInTheDocument();
                    play.expect(screen.getByText("Updated")).toBeInTheDocument();
                    play.expect(screen.getByText("stroom")).toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText("UUID")).toBeNull());
                    expectNoProblems(play);
                })
                // Export: the dialog's tree has the node checked; OK exports it and downloads the zip
                .story("ExportDocument", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.post(EXPORT_PATH, RestReply.json(
                                "{\"resourceKey\": {\"key\": \"res-1\", \"name\": \"export.zip\"}, "
                                + "\"messageList\": []}")),
                        builder -> builder.appPermissions(AppPermission.EXPORT_CONFIGURATION), null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.click(screen.findByText("Export", StroomDom.MENU_ITEM_TEXT));
                    final Query dialog = screen.findByText("Export", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG);
                    // Differs from React: the quick filter has no label, only its placeholder
                    play.expect(play.within(dialog).findByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER))
                            .toBeInTheDocument();
                    final Query typeFilter = play.within(dialog).getByRole("button", "Filter Types");
                    play.expect(typeFilter).not().toHaveClass("on");
                    // The Filter Types button is beside the quick filter, in the same row.
                    // Differs from React: the row is ExportConfigViewImpl's '.exportConfigViewImpl-filter'
                    play.expect(typeFilter.closest(".exportConfigViewImpl-filter")).not().toBeNull();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(EXPORT_PATH)
                                    .withJsonBodyContaining("{\"docRefs\": [{\"uuid\": \"dict-countries\"}]}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.DOWNLOAD_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("res-1")));
                    expectNoProblems(play);
                })
                // Rename renames the document and relabels its open tab
                // (the server's rename is seen when the editor reloads the document)
                .story("RenameDocument", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(DICTIONARY_PATH + "dict-countries", RestReply.json(COUNTRIES),
                                RestReply.json(COUNTRIES.replace("\"Countries\"", "\"Nations\""))),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    play.dblClick(nav.getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    rightClick(play, nav.getByText("Countries"));
                    play.click(screen.findByText("Rename", StroomDom.MENU_ITEM_TEXT));
                    final Query name = screen.findByLabelText("Name");
                    play.clear(name);
                    play.type(name, "Nations");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(RENAME_PATH)
                                    .withJsonBodyContaining("{\"docName\": \"Nations\"}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByLabelText("Name")).toBeNull());
                    // The editor reloads the renamed document, and its tab is relabelled (it once kept
                    // the old label, as DocTabPresenter.onRead didn't refresh the tab when the name
                    // changed)
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(() -> play.expect("document fetches", () -> countCalls(requests,
                            "GET " + DICTIONARY_PATH + "dict-countries")).toBe(2));
                    play.waitFor(() -> play.expect(tabs(play).nth(0)).toHaveTextContent("Nations"));
                    expectNoProblems(play);
                })
                // A blank name is refused with a warning, and the rename dialog stays open
                .story("RenameToBlankWarns", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.click(screen.findByText("Rename", StroomDom.MENU_ITEM_TEXT));
                    final Query name = screen.findByLabelText("Name");
                    play.clear(name);
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText("You must provide a new name for Countries"))
                            .toBeInTheDocument());
                    play.expect(screen.getByLabelText("Name")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Delete confirms, deletes and closes the deleted document's tab
                .story("DeleteDocument", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    play.dblClick(nav.getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    rightClick(play, nav.getByText("Countries"));
                    play.click(screen.findByText("Delete", StroomDom.MENU_ITEM_TEXT));
                    play.expect(screen.findByText(TextMatch.containingIgnoreCase(
                            "are you sure you want to delete this item"))).toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(tabs(play)).toHaveLength(0));
                    expectDeleted(play, "dict-countries");
                    expectNoProblems(play);
                })
                // The toolbar's Delete is enabled by a selection, and deletes it
                .story("ToolbarDelete", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    final Query delete = nav.getByRole("button", "Delete");
                    play.expect(delete).toHaveAttribute("aria-disabled", "true");
                    play.click(nav.getByText("Countries"));
                    play.waitFor(() -> play.expect(delete).not().toHaveAttribute("aria-disabled"));
                    play.click(delete);
                    play.expect(screen.findByText(TextMatch.containingIgnoreCase(
                            "are you sure you want to delete this item"))).toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText(TextMatch.containingIgnoreCase(
                            "are you sure you want to delete this item"))).toBeNull());
                    expectDeleted(play, "dict-countries");
                    expectNoProblems(play);
                })
                // New: the document types menu, the create dialog, and the new document opens
                .story("CreateDocument", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(StartupFixtures.DOCUMENT_TYPES_PATH, RestReply.json(DICTIONARY_TYPES))
                                .get(DICTIONARY_PATH + "new-doc", RestReply.json(newDoc("Regions"))),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    final Query newButton = nav.getByRole("button", "New");
                    play.waitFor(() -> play.expect(newButton).toHaveAttribute("aria-disabled", "true"));
                    play.click(nav.getByText("Dictionaries"));
                    play.waitFor(() -> play.expect(newButton).not().toHaveAttribute("aria-disabled"));
                    play.click(newButton);
                    play.click(screen.findByText("Dictionary", StroomDom.MENU_ITEM_TEXT));
                    final Query dialog = screen.findByText("New Dictionary", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG);
                    // Pick the destination folder in the dialog's tree
                    play.click(play.within(dialog).findByText("Dictionaries"));
                    play.type(play.within(dialog).getByLabelText("Name"), "Regions");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(5000, () -> play.expect(play.getByText("Regions", TAB_LABEL)).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // A multiple selection is deleted together, with the count in the confirmation
                .story("MultiDelete", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    play.click(nav.getByText("Countries"));
                    // Differs from React: GWT's tree selects on mousedown, so Ctrl is held on the
                    // mousedown
                    play.fireEvent().mouseDown(nav.getByText("Colours"), EventInit.create().ctrlKey());
                    rightClick(play, nav.getByText("Countries"));
                    play.click(screen.findByText("Delete", StroomDom.MENU_ITEM_TEXT));
                    play.expect(screen.findByText(TextMatch.containingIgnoreCase(
                            "are you sure you want to delete these 2 items"))).toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText(TextMatch.containingIgnoreCase(
                            "delete these 2 items"))).toBeNull());
                    expectNoProblems(play);
                })
                // Copy of one item: a name box filled with its name, and its folder chosen
                .story("CopyDocument", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.click(screen.findByText("Copy", StroomDom.MENU_ITEM_TEXT));
                    screen.findByText("Copy Countries", StroomDom.DIALOG_TITLE);
                    final Query name = screen.getByLabelText("Name");
                    play.waitFor(() -> play.expect(name).toHaveValue("Countries"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Copy Countries", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(COPY_PATH).toSpyMatcher());
                    expectNoProblems(play);
                })
                // Move: the folder chosen, no name box
                .story("MoveDocument", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.click(screen.findByText("Move", StroomDom.MENU_ITEM_TEXT));
                    screen.findByText("Move Countries", StroomDom.DIALOG_TITLE);
                    play.expect(screen.queryByLabelText("Name")).toBeNull();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Move Countries", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(MOVE_PATH).toSpyMatcher());
                    expectNoProblems(play);
                })
                // On a node the user may only view, Rename, Move and Delete are disabled
                .story("ContextMenuPermissions", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.post(PERMISSIONS_PATH, request -> RestReply.json(
                                AppShellFixtures.explorerPermissions(request.getBody(),
                                        AppShellFixtures.VIEW_ONLY, List.of()))),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.waitFor(() -> play.expect(menuItem(screen, "Delete")).toHaveClass(MENU_ITEM_DISABLED));
                    play.expect(menuItem(screen, "Rename")).toHaveClass(MENU_ITEM_DISABLED);
                    play.expect(menuItem(screen, "Copy")).not().toHaveClass(MENU_ITEM_DISABLED);
                    expectNoProblems(play);
                })
                // A deep link opens its document at start-up; Alt+W closes it
                .story("DeepLink", context -> render(context, AppShellFixtures.dictTree(), routes -> {
                }, builder -> {
                }, new DocRef("Dictionary", "dict-countries")))
                .withPlay(play -> {
                    final Query words = play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    // The deep link's name came from the explorer's decorate
                    play.expect(play.findByText("Countries", TAB_LABEL)).toBeInTheDocument();
                    play.click(words);
                    play.keyboard("{Alt>}w{/Alt}");
                    play.waitFor(() -> play.expect(tabs(play)).toHaveLength(0));
                    expectNoProblems(play);
                })
                // The explorer's tree comes from the explorer's fetch, with its toolbar
                .story("LiveExplorer", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    play.expect(play.findByText("Dictionaries")).toBeInTheDocument();
                    play.expect(play.getByRole("button", TextMatch.regex("expand all", "i"))).toBeInTheDocument();
                    play.expect(play.getByRole("button", TextMatch.regex("collapse all", "i"))).toBeInTheDocument();
                    play.expect(play.getByRole("button", TextMatch.regex("^new$", "i"))).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Sign Out Other Sessions confirms first, then signs out the others
                .story("SignOutOtherSessions", context -> render(context, TreeFixtures.fixtureTree(),
                        routes -> routes.post(TERMINATE_OTHER_PATH, RestReply.json("true")),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Cancelling doesn't sign out the other sessions
                    openUserMenu(play);
                    play.click(screen.findByText("Sign Out Other Sessions", StroomDom.MENU_ITEM_TEXT));
                    screen.findByText(TextMatch.regex("Sign out of all your other sessions", "i"));
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(TERMINATE_OTHER_PATH).toSpyMatcher());
                    // Confirming signs them out and says so
                    openUserMenu(play);
                    play.click(screen.findByText("Sign Out Other Sessions", StroomDom.MENU_ITEM_TEXT));
                    screen.findByText(TextMatch.regex("Sign out of all your other sessions", "i"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(TERMINATE_OTHER_PATH).toSpyMatcher()));
                    screen.findByText("Signed out of your other sessions.");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A folder with contents and dependants gets the warning listing them
                .story("DeleteWithDependantsWarns", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.post(DELETE_CONFIRMATION_PATH, RestReply.json(DELETE_WITH_DEPENDANTS)),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.click(screen.findByText("Delete", StroomDom.MENU_ITEM_TEXT));
                    play.expect(screen.findByText(TextMatch.containingIgnoreCase(
                            "contains other items and is used by items elsewhere"))).toBeInTheDocument();
                    play.expect(screen.getByText("The following 2 contained items will also be deleted:"))
                            .toBeInTheDocument();
                    play.expect(screen.getByText("Dictionary (2)")).toBeInTheDocument();
                    play.expect(screen.getByText("Contained One")).toBeInTheDocument();
                    play.expect(screen.getByText("Dependent Pipeline")).toBeInTheDocument();
                    play.expect(screen.getByText(TextMatch.containingIgnoreCase(
                            "There are also dependants that you do not have permission to view")))
                            .toBeInTheDocument();
                    expectNoProblems(play);
                })
                // A failed lookup still warns and still lets the delete go ahead
                .story("DeleteAfterFailedLookupStillProceeds", context -> render(context,
                        AppShellFixtures.dictTree(),
                        routes -> routes.post(DELETE_CONFIRMATION_PATH,
                                RestReply.error(500, "Dependency service unavailable")),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    play.dblClick(nav.getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    rightClick(play, nav.getByText("Countries"));
                    play.click(screen.findByText("Delete", StroomDom.MENU_ITEM_TEXT));
                    play.expect(screen.findByText(TextMatch.containingIgnoreCase(
                            "Unable to check what would be affected by deleting this item"))).toBeInTheDocument();
                    play.expect(screen.getByText(TextMatch.containing("Dependency service unavailable")))
                            .toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(tabs(play)).toHaveLength(0));
                    expectDeleted(play, "dict-countries");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Manage Users only: User Access is offered, Signing Keys (administrators) isn't
                .story("SecurityMenuUserAccessOnly", context -> render(context, TreeFixtures.fixtureTree(),
                        routes -> {
                        }, builder -> builder.appPermissions(AppPermission.MANAGE_USERS_PERMISSION), null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "Security")).toBeInTheDocument());
                    play.hover(menuItem(screen, "Security"));
                    play.expect(screen.findByText("User Access", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    play.expect(screen.queryByText("Signing Keys", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    expectNoProblems(play);
                })
                // An administrator sees both, with their shortcuts
                .story("SecurityMenuBothForAdministrator", context -> render(context, TreeFixtures.fixtureTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "Security")).toBeInTheDocument());
                    play.hover(menuItem(screen, "Security"));
                    play.expect(screen.findByText("User Access", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    play.expect(screen.findByText("Signing Keys", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    play.expect(shortcuts(play)).toContain("gw");
                    play.expect(shortcuts(play)).toContain("gy");
                    expectNoProblems(play);
                })
                // Locate in Explorer is offered only for a tab the explorer can hold, as the
                // document plugin registry decides
                .story("LocateOnlyForExplorerTabs", AppShellStories::renderLocate)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(navTree(play).getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.fireEvent().mouseUp(play.getByText("Countries", TAB_LABEL), EventInit.create().button(2));
                    play.waitFor(() -> play.expect(menuItem(screen, "Locate in Explorer"))
                            .not().toHaveClass(MENU_ITEM_DISABLED));
                    play.keyboard("{Escape}");
                    // Differs from React: the rule is Stroom's DocumentPluginRegistry.getExplorerDocRef,
                    // checked with the shell's registry (the Dictionary plugin is registered, no
                    // plugin is registered for a User screen's tab or an unknown type)
                    play.expect(play.spy(LOCATE_SPY)).toHaveBeenCalledWith("Dictionary: Countries");
                    play.expect(play.spy(LOCATE_SPY)).toHaveBeenCalledWith("User: null");
                    play.expect(play.spy(LOCATE_SPY)).toHaveBeenCalledWith("Nope: null");
                    expectNoProblems(play);
                });
    }

    // --------------------------------------------------------------------------------
    // Rendering

    private static Widget render(final StoryContext context, final ExplorerFixture tree) {
        return render(context, tree, routes -> {
        }, builder -> {
        }, null);
    }

    private static Widget render(final StoryContext context,
                                 final ExplorerFixture tree,
                                 final Consumer<RestFixtures.Builder> storyRoutes,
                                 final Consumer<ScreenHarness.Builder> options,
                                 final DocRef initialDocRef) {
        final ShellScreen shell = createShell(context, tree, storyRoutes, options);
        shell.start(initialDocRef);
        return shell.asWidget();
    }

    private static ShellScreen createShell(final StoryContext context,
                                           final ExplorerFixture tree,
                                           final Consumer<RestFixtures.Builder> storyRoutes,
                                           final Consumer<ScreenHarness.Builder> options) {
        final RestFixtures.Builder routes = RestFixtures.builder();
        storyRoutes.accept(routes);
        return ShellScreen.create(context, routes.addAll(commonRoutes(tree)).build(), options);
    }

    // Stroom's embedded view: the document without the shell
    private static Widget renderEmbedded(final StoryContext context) {
        final ShellScreen shell = createShell(context, TreeFixtures.fixtureTree(),
                routes -> routes.get(DICTIONARY_PATH + "d-1", RestReply.json(COUNTRIES
                        .replace("dict-countries", "d-1")
                        .replace("\"Countries\"", "\"Embedded\""))),
                builder -> {
                });
        shell.startFullScreen(new DocRef("Dictionary", "d-1", "Embedded"));
        return shell.asWidget();
    }

    // The shell, and the registry's answers for a Dictionary tab, a User screen's tab and a tab of
    // an unknown type, recorded once the shell has started
    private static Widget renderLocate(final StoryContext context) {
        final ShellScreen shell = createShell(context, AppShellFixtures.dictTree(), routes -> {
        }, builder -> {
        });
        final ScreenHarness harness = shell.getHarness();
        harness.fn(LOCATE_SPY);
        shell.start(null);
        harness.afterStartUp(() -> {
            final DocumentPluginRegistry registry = shell.getInjector().getDocumentPluginRegistry();
            for (final DocRef docRef : List.of(
                    new DocRef("Dictionary", "d-1", "Countries"),
                    new DocRef("User", "user:u-1", "admin"),
                    new DocRef("Nope", "x", "x"))) {
                final DocRef explorerDocRef = registry.getExplorerDocRef(new StandInTab(docRef));
                harness.spy(LOCATE_SPY, docRef.getType() + ": " + (explorerDocRef == null
                        ? null
                        : explorerDocRef.getName()));
            }
        });
        return shell.asWidget();
    }

    // What the shell asks for whatever the story (React's shared fixtures): the explorer's tree,
    // decorations and permissions (React's crudFixture: the user owns every node and may create
    // dictionaries), the explorer's actions, the activities (none), and the Dictionary, Feed and
    // Folder editors' data
    private static RestFixtures commonRoutes(final ExplorerFixture tree) {
        final Map<String, String> names = new HashMap<>();
        for (final String name : List.of("Countries", "Colours", "EVENTS", "Dictionaries")) {
            try {
                names.put(tree.get(name).getUuid(), name);
            } catch (final IllegalArgumentException e) {
                // Not in this tree
            }
        }
        final RestFixtures.Builder builder = ActivityFixtures.common(TreeFixtures.explorerRoutes(tree), null)
                .post(TreeFixtures.DECORATE, request -> RestReply.json(
                        AppShellFixtures.decorate(names, request.getBody())))
                .post(PERMISSIONS_PATH, request -> RestReply.json(
                        AppShellFixtures.explorerPermissions(request.getBody(),
                                AppShellFixtures.FULL_PERMISSIONS,
                                List.of("Dictionary"))))
                .get(ActivityFixtures.PATH, RestReply.json(ActivityFixtures.page()))
                // The main menu's result stores (ResultStoreModel lists the nodes when it is built)
                .get("/node/v1/all", RestReply.json("[\"node1a\"]"))
                .route(RequestMatcher.post("/result-store/v1/find/*"), RestReply.json(EMPTY_PAGE))
                // The explorer's actions (React's crudFixture)
                .post(CREATE_PATH, request -> RestReply.json(AppShellFixtures.created(request.getBody())))
                .put(RENAME_PATH, request -> RestReply.json(AppShellFixtures.renamed(request.getBody())))
                .delete(DELETE_PATH, request -> RestReply.json(AppShellFixtures.deleted(request.getBody())))
                .post(DELETE_CONFIRMATION_PATH, RestReply.json("{}"))
                .post(COPY_PATH, RestReply.json("{\"explorerNodes\": [], \"message\": \"\"}"))
                .put(MOVE_PATH, RestReply.json("{\"explorerNodes\": [], \"message\": \"\"}"))
                .post("/explorer/v2/info", RestReply.json(INFO))
                // The Dictionary editor
                .get(DICTIONARY_PATH + "dict-countries", RestReply.json(COUNTRIES))
                .get(DICTIONARY_PATH + "dict-colours", RestReply.json(COUNTRIES
                        .replace("dict-countries", "dict-colours")
                        .replace("\"Countries\"", "\"Colours\"")))
                .route(RequestMatcher.put(DICTIONARY_PATH + "*"), request -> RestReply.json(request.getBody()))
                .route(RequestMatcher.get("/wordList/v1/*"), RestReply.json(WORDS))
                // The Feed editor
                .get("/feed/v1/feed-events", RestReply.json(FEED))
                .get("/feed/v1/fetchSupportedEncodings", RestReply.json("[\"UTF-8\", \"ASCII\"]"))
                .get("/meta/v1/getTypes", RestReply.json("[\"Raw Events\", \"Events\"]"))
                .post("/fsVolume/volumeGroup/v2/find", RestReply.json("{\"values\": [{\"id\": 1, \"name\": "
                        + "\"Default\"}], \"pageResponse\": {\"offset\": 0, \"length\": 1, \"total\": 1, "
                        + "\"exact\": true}}"))
                // The Data, Processors and Active Tasks tabs (Feed and Folder)
                .post("/meta/v1/find", RestReply.json(STREAMS))
                .post("/processorFilter/v1/find", RestReply.json(EMPTY_PAGE))
                .post("/processorTask/v1/find", RestReply.json(EMPTY_PAGE))
                .post("/processorTask/v1/summary", RestReply.json(EMPTY_PAGE));
        return DocEditors.permissionRoutes(builder).build();
    }

    // DocumentTypes: the types, all of them visible in the type filter
    private static String documentTypes(final String types) {
        return "{\"types\": [" + types + "], \"visibleTypes\": [" + types + "]}";
    }

    // A document created by the explorer's create (uuid new-doc), as the Dictionary editor fetches it
    private static String newDoc(final String name) {
        return COUNTRIES.replace("dict-countries", "new-doc").replace("\"Countries\"", "\"" + name + "\"");
    }

    // --------------------------------------------------------------------------------
    // Play helpers

    // Waits for the shell to show the explorer, and returns a query scoped to it
    private static Play navTree(final Play play) {
        return navTree(play, "Dictionaries");
    }

    private static Play navTree(final Play play, final String node) {
        play.findByText(node);
        return play.within(play.querySelector(NAVIGATION));
    }

    private static Query tabs(final Play play) {
        return play.queryAllByText(TextMatch.regex(".", ""), TAB_LABEL);
    }

    private static Query menuItem(final Play screen, final String title) {
        return screen.querySelector(MENU_ITEM + "[title=\"" + title + "\"]");
    }

    private static Supplier<List<String>> shortcuts(final Play play) {
        return play.screen().querySelectorAll(MENU_SHORTCUT).textContents();
    }

    // Differs from React: the explorer's menu is shown on a secondary button's mousedown, not a
    // contextmenu event
    private static void rightClick(final Play play, final Query node) {
        play.rightClick(node);
    }

    private static void openUserMenu(final Play play) {
        final Play screen = play.screen();
        play.click(play.findByTitle(MAIN_MENU));
        play.waitFor(() -> play.expect(menuItem(screen, "User")).toBeInTheDocument());
        play.hover(menuItem(screen, "User"));
    }

    // Selects an import on the Imports tab and removes it (confirmed)
    private static void removeImport(final Play play, final String name) {
        final Play screen = play.screen();
        play.click(play.findByText(name));
        play.click(play.getByTitle("Remove Import"));
        play.click(screen.findByRole("button", StroomDom.button("OK")));
    }

    private static void expectDeleted(final Play play, final String uuid) {
        play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                RequestMatcher.delete(DELETE_PATH)
                        .withJsonBodyContaining("{\"docRefs\": [{\"uuid\": \"" + uuid + "\"}]}")
                        .toSpyMatcher()));
    }

    // The number of requests recorded by the request spy that start with the given method and path
    private static int countCalls(final Spy requests, final String prefix) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            if (!call.isEmpty() && String.valueOf(call.get(0)).startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    private static void expectNoProblems(final Play play) {
        ContentStorySupport.expectNoProblems(play);
    }

    // --------------------------------------------------------------------------------


    /// A tab showing a document, as a document editor's tab is to the registry.
    private static final class StandInTab implements DocumentTabData {

        private final DocRef docRef;

        private StandInTab(final DocRef docRef) {
            this.docRef = docRef;
        }

        @Override
        public DocRef getDocRef() {
            return docRef;
        }

        @Override
        public SvgImage getIcon() {
            return SvgImage.DOCUMENT_DICTIONARY;
        }

        @Override
        public String getLabel() {
            return docRef.getName();
        }

        @Override
        public boolean isCloseable() {
            return true;
        }

        @Override
        public String getType() {
            return docRef.getType();
        }
    }
}
