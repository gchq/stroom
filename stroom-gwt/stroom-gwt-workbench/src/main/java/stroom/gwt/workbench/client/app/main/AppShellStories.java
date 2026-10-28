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
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.client.a11y.ButtonRuns;
import stroom.gwt.workbench.client.app.ai.AiFixtures;
import stroom.gwt.workbench.client.app.ai.AskStroomAiChat;
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
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Stories of `App/Main/AppShell`: Stroom's app shell put together by [ShellScreen] from Stroom's
/// real controllers and plugins: the main view (its main menu), the explorer (`NavigationController`:
/// the tree, its toolbar and the current activity), the document tabs (`ContentTabPaneController`),
/// the explorer's and tabs' menus and document actions (`DocumentPluginEventManager`), keyboard
/// shortcuts (`GlobalKeyHandlerImpl`), and the Dictionary, Feed and Folder editors.
///
/// The Stroom endpoints the stories answer:
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /explorer/v2/fetchExplorerNodes` ([ExplorerFixture]) | the explorer's tree |
/// | the start-up fixtures | the session, UI config, preferences and app permissions |
/// | `GET /explorer/v2/fetchDocumentTypes` | the document types |
/// | `POST /explorer/v2/fetchExplorerPermissions` | the explorer's permissions |
/// | `/explorer/v2/` + `create`, `rename`, ... | create, rename, copy, move, remove and info |
/// | `POST /explorer/v2/fetchDeleteConfirmation` | the delete confirmation |
/// | `POST /explorer/v2/decorate` | naming the deep-linked document (`ShowMainEvent`'s document) |
/// | `GET`/`PUT /dictionary/v1/{uuid}`, `GET /wordList/v1/{uuid}` | the Dictionary editor |
/// | `/feed/v1/...`, `GET /meta/v1/getTypes`, `POST /fsVolume/volumeGroup/v2/find` | the Feed editor |
/// | `POST /meta/v1/find`, `/processorTask/v1/...` | the streams and processor tasks |
/// | the Permissions tab's `/permission/doc/v1/...` | document permissions |
/// | `/activity/v1/...` (`setCurrent` is checked on the request spy) | the current activity |
/// | `POST /content/v1/export`, the download spy | exporting content |
/// | `POST /session/v1/terminateOther` | ending the user's other sessions |
public final class AppShellStories {

    /// The name of the spy recording the registry's answers in `LocateOnlyForExplorerTabs`.
    static final String LOCATE_SPY = "getExplorerDocRef";

    // The explorer panel is Navigation's '.navigation'
    private static final String NAVIGATION = ".navigation";
    // The document tabs' labels
    private static final String TAB_LABEL = ".tab-bar--curved .tab-bar__label";
    // A menu item's row (MenuItemCell), inside the element with its role and aria-disabled
    private static final String MENU_ITEM = ".menu__item";
    private static final String MENU_ITEM_ROLE = ":is([role='menuitem'], [role='menuitemcheckbox'])";
    private static final String MENU_SHORTCUT = ".menu__shortcut";
    // The main menu button is the explorer's 'Main Menu'
    private static final String MAIN_MENU = "Main Menu";
    // The content tab pane (CurveTabLayout) when the focus is in it
    private static final String CONTENT_WITH_FOCUS = ".curveTabLayoutViewImpl-layerContainerOuter:focus-within";

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

    // A dictionary with fixed words and two imports (so the lifecycle story can edit by
    // removing them)
    private static final String COUNTRIES = """
            {"type": "Dictionary", "uuid": "dict-countries", "name": "Countries",
              "description": "ISO country names", "data": "England\\nScotland\\nWales",
              "imports": [{"type": "Dictionary", "uuid": "dict-alpha", "name": "Alpha"},
                          {"type": "Dictionary", "uuid": "dict-beta", "name": "Beta"}]}""";
    // Its word list
    private static final String WORDS = """
            {"wordList": [{"word": "sample", "sourceUuid": "dict-countries"}], "sourceUuidToDocRefMap": {}}""";

    // A feed
    private static final String FEED = """
            {"type": "Feed", "uuid": "feed-events", "name": "EVENTS", "status": "RECEIVE",
              "encoding": "UTF-8", "dataFormat": "JSON", "streamType": "Raw Events", "reference": false}""";
    // The feed's streams
    private static final String STREAMS = """
            {"values": [{"meta": {"id": 101, "feedName": "EVENTS", "typeName": "Raw Events",
                "status": "UNLOCKED", "createMs": 1700000000000}, "attributes": {}}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";
    // The explorer's Info for the dictionary
    private static final String INFO = """
            {"explorerNode": {"type": "Dictionary", "uuid": "dict-countries", "name": "Countries",
                "tags": ["stroom"]},
              "auditEntries": {"values": [
                  {"time": 1700000000000, "user": {"displayName": "admin"}, "action": "CREATE"},
                  {"time": 1700100000000, "user": {"displayName": "admin"}, "action": "UPDATE"}],
                "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}}""";
    // The document types
    private static final String FEED_TYPE = """
            {"group": "DATA_PROCESSING", "type": "Feed", "displayType": "Feed", "icon": "DOCUMENT_FEED"}""";
    private static final String DICTIONARY_TYPE = """
            {"group": "STRUCTURE", "type": "Dictionary", "displayType": "Dictionary", "icon": "DOCUMENT_DICTIONARY"}""";
    private static final String FEED_AND_DICTIONARY_TYPES = documentTypes(FEED_TYPE + ", " + DICTIONARY_TYPE);
    private static final String DICTIONARY_TYPES = documentTypes(DICTIONARY_TYPE);
    // A delete confirmation for a folder with dependants
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
                // The explorer's Filter Document Types button opens the type filter (the document types);
                // turning a type off restricts the tree and lights the button
                .story("ExplorerTypeFilter", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(StartupFixtures.DOCUMENT_TYPES_PATH,
                                RestReply.json(FEED_AND_DICTIONARY_TYPES)),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play nav = navTree(play);
                    final Query filter = nav.getByRole("button", "Filter Document Types");
                    // The toggle button says whether a filter is set with aria-pressed
                    play.expect(filter).toHaveAttribute("aria-pressed", "false");
                    play.click(filter);
                    final Play screen = play.screen();
                    screen.findByText("All / None");
                    play.expect(screen.getByText("Feed")).toBeInTheDocument();
                    play.expect(screen.getByText("Dictionary")).toBeInTheDocument();
                    play.click(screen.getByText("Feed"));
                    play.waitFor(() -> play.expect(filter).toHaveAttribute("aria-pressed", "true"));
                    expectNoProblems(play);
                })
                // The Show Alerts button shows only when dependency warnings are enabled
                .story("ExplorerToggleAlerts", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> {
                        },
                        builder -> builder.startup(startup -> startup.extendedUiConfig(
                                "{\"uiConfig\": " + StartupFixtures.DEFAULT_UI_CONFIG
                                + ", \"dependencyWarningsEnabled\": true}")),
                        null))
                .withPlay(play -> {
                    final Play nav = navTree(play);
                    final Query alerts = nav.getByRole("button", "Show Alerts");
                    play.expect(alerts).toHaveAttribute("aria-pressed", "false");
                    play.click(alerts);
                    play.waitFor(() -> play.expect(alerts).toHaveAttribute("aria-pressed", "true"));
                    expectNoProblems(play);
                })
                // A document opened on its own, without the explorer or toolbar
                .story("Embedded", AppShellStories::renderEmbedded)
                .withPlay(play -> {
                    // Stroom shows a document without the shell for a URL that
                    // names it without the open-doc action (CoreController); with open-doc (a deep
                    // link) it shows the whole shell, embedded or not. So the story opens the
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
                    // GWT's Find dialog's quick filter has no label; the
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
                // A user who may manage nodes, but nothing that the Administration group holds, has
                // no Administration group. The Node Groups and Processor Profiles plugins added an
                // empty one, as well as their items in Monitoring
                .story("MainMenuNoEmptyGroup", context -> render(context, TreeFixtures.fixtureTree(),
                        routes -> {
                        }, builder -> builder.appPermissions(AppPermission.MANAGE_NODES_PERMISSION), null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "Monitoring")).toBeInTheDocument());
                    play.expect(menuItem(screen, "Administration")).toBeNull();
                    play.hover(menuItem(screen, "Monitoring"));
                    play.waitFor(() -> play.expect(menuItem(screen, "Node Groups")).toBeInTheDocument());
                    play.expect(menuItem(screen, "Processor Profiles")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // The Help group's items open pages in a new browser tab, which their names say
                // (in hidden text, after their visible text)
                .story("HelpMenu", context -> render(context, TreeFixtures.fixtureTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "Help")).toBeInTheDocument());
                    play.hover(menuItem(screen, "Help"));
                    final Query help = screen.findByRole("menuitem", "Help (opens in new tab)");
                    play.expect(help).not().toHaveAttribute("aria-disabled");
                    play.expect(screen.getByRole("menuitem", "API Specification (opens in new tab)"))
                            .toBeInTheDocument();
                    expectNoProblems(play);
                })
                // With no help URL configured, the Help group's 'Help is not configured!' is shown
                // disabled, as other unavailable items are, rather than as an item that does nothing
                .story("HelpNotConfigured", context -> render(context, TreeFixtures.fixtureTree(),
                        routes -> {
                        }, builder -> builder.uiConfig(StartupFixtures.DEFAULT_UI_CONFIG
                                .replace("\"helpUrl\": \"https://gchq.github.io/stroom-docs/7.13/docs\",", "")),
                        null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "Help")).toBeInTheDocument());
                    play.hover(menuItem(screen, "Help"));
                    final Query notConfigured = screen.findByRole("menuitem", "Help is not configured!");
                    play.expect(notConfigured).toHaveAttribute("aria-disabled", "true");
                    play.expect(menuItem(screen, "Help is not configured!")).toHaveAttribute("aria-disabled", "true");
                    expectNoProblems(play);
                })
                // The menu shows the keyboard shortcuts, and 'g' then 'u' opens the preferences
                .story("GotoShortcut", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Dictionaries");
                    play.click(play.getByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "User")).toBeInTheDocument());
                    // GWT shows a group's items (with their shortcuts) only
                    // while the group is hovered, so each group is hovered in turn
                    play.hover(menuItem(screen, "User"));
                    play.waitFor(() -> play.expect(shortcuts(play)).toContain("G U"));
                    play.hover(menuItem(screen, "Tools"));
                    play.waitFor(() -> play.expect(shortcuts(play)).toContain("G D"));
                    play.hover(menuItem(screen, "Navigation"));
                    play.waitFor(() -> play.expect(shortcuts(play)).toContain("Alt+Shift+F"));
                    play.expect(shortcuts(play)).toContain("Ctrl+Shift+F");
                    play.expect(shortcuts(play)).toContain("Ctrl+E");
                    play.expect(shortcuts(play)).toContain("Alt+L");
                    // Escape closes the open sub menu, then the menu: while a menu is open, typed
                    // letters go to its items (type-ahead) rather than to the shortcuts
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.getAllByRole("menu")).toHaveLength(1));
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByRole("menu")).toBeNull());
                    // GWT's key sequences complete on the keys' release, so
                    // the keys are pressed and released rather than only pressed
                    play.keyboard("gu");
                    play.waitFor(() -> play.expect(screen.getByText("User Preferences", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    expectNoProblems(play);
                })
                // The preferences offer every Ace editor theme of the Stroom theme's type, including
                // those Ace 1.44 added. The user's theme is the workbench's, which the harness makes
                // the current one, so that changing it in the dialog is a change, as in Stroom.
                .story("EditorThemePreferences", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> {
                        }, builder -> builder.startup(startup -> startup.userPreferences(
                                StartupFixtures.DEFAULT_USER_PREFERENCES.replace("\"theme\": \"Light\"",
                                        "\"theme\": \"" + StroomThemeDecorator.getStroomThemeName() + "\""))),
                        null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Dictionaries");
                    play.keyboard("gu");
                    final Play dialog = screen.within(screen.findByText("User Preferences", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    chooseTheme(play, dialog, "Light");
                    expectEditorThemes(play, dialog, "Stroom light", "Chrome", "Cloud9 day", "Cloud editor",
                            "Github light default", "Gruvbox light hard");
                    chooseTheme(play, dialog, "Dark");
                    // Choosing the theme that's already chosen closes the list too
                    chooseTheme(play, dialog, "Dark");
                    expectEditorThemes(play, dialog, "Stroom dark", "Ambiance", "Cloud9 night",
                            "Cloud9 night low color", "Cloud editor dark", "Github dark", "Gruvbox dark hard",
                            "One dark");
                    expectNoProblems(play);
                })
                // The Date and Time tab: a time zone given as an offset shows the offset's hours,
                // named for what they are (the minutes are hidden: browsers don't support them)
                .story("DateAndTimePreferences", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Dictionaries");
                    play.keyboard("gu");
                    final Play dialog = screen.within(screen.findByText("User Preferences", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.click(dialog.getByText("Date and Time", StroomDom.LINK_TAB_LABEL));
                    final Query timeZone = dialog.querySelector("#userPreferencesTimeZone .selection-box__input");
                    play.waitFor(() -> play.expect(timeZone).toHaveAttribute("role", "combobox"));
                    play.click(timeZone);
                    play.click(screen.findByText("Offset", ".selection-popup .selectionItemCell-text"));
                    play.waitFor(() -> play.expect(dialog.getByRole("spinbutton", "Hours")).toBeVisible());
                    play.expect(dialog.queryByRole("spinbutton", "Minutes")).toBeNull();
                    expectNoProblems(play);
                })
                // The Keyboard Shortcuts tab lists each action the user can bind, one row per
                // shortcut, with the built-in arrow keys that can't be changed
                .story("KeyboardShortcutPreferences", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play dialog = openKeyboardShortcuts(play);
                    play.expect(dialog.getByText("Recent items")).toBeInTheDocument();
                    play.expect(dialog.getByText("Ctrl+E")).toBeInTheDocument();
                    play.expect(dialog.getByText("G U")).toBeInTheDocument();
                    play.expect(dialog.getAllByText("Built in").count()).toBeGreaterThan(0);
                    play.expect(dialog.getByRole("checkbox", "Enable Character Key Shortcuts")).toBeChecked();
                    expectNoProblems(play);
                })
                // Pressing new keys for 'Recent items' saves them in the preferences, and the
                // Navigation menu then shows them
                .story("ChangeKeyboardShortcut", context -> render(context, AppShellFixtures.dictTree(),
                        AppShellStories::savePreferencesRoute, builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = openKeyboardShortcuts(play);
                    final Play recorder = recordShortcut(play, dialog, "Recent items");
                    play.keyboard("{Control>}{Shift>}e{/Shift}{/Control}");
                    play.waitFor(() -> play.expect(recorder.getByLabelText("Keys for 'Recent items'"))
                            .toHaveValue("Ctrl+Shift+E"));
                    // Enter accepts the keys, as the recorder says
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(dialog.getByText("Ctrl+Shift+E")).toBeInTheDocument());
                    play.expect(dialog.getByText("Changed")).toBeInTheDocument();
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/preferences/v1")
                                    .withJsonBodyContaining("{\"keyBindings\": {\"RECENT_ITEMS\": [\"Ctrl+Shift+E\"]}}")
                                    .toSpyMatcher()));
                    play.click(play.getByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "Navigation")).toBeInTheDocument());
                    play.hover(menuItem(screen, "Navigation"));
                    play.waitFor(() -> play.expect(shortcuts(play)).toContain("Ctrl+Shift+E"));
                    play.expect(shortcuts(play)).not().toContain("Ctrl+E");
                    expectNoProblems(play);
                })
                // Keys another action uses are moved from it, after the recorder says so, and keys
                // that can't be changed are refused
                .story("RecordClashingShortcut", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play dialog = openKeyboardShortcuts(play);
                    final Play recorder = recordShortcut(play, dialog, "Locate in explorer");
                    play.keyboard("{Control>}a{/Control}");
                    play.waitFor(() -> play.expect(recorder.getByText(TextMatch.containing(
                            "Ctrl+A is used for 'Select all', which can't be changed"))).toBeInTheDocument());
                    play.keyboard("{Control>}e{/Control}");
                    play.waitFor(() -> play.expect(recorder.getByText(TextMatch.containing(
                            "Ctrl+E clashes with the shortcut for 'Recent items' (Ctrl+E)"))).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // Turning character key shortcuts off marks them as off, and the menu no longer
                // shows them
                .story("CharacterKeyShortcutsOff", context -> render(context, AppShellFixtures.dictTree(),
                        AppShellStories::savePreferencesRoute, builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = openKeyboardShortcuts(play);
                    play.click(dialog.getByRole("checkbox", "Enable Character Key Shortcuts"));
                    play.waitFor(() -> play.expect(dialog.getAllByText("Default, turned off").count())
                            .toBeGreaterThan(0));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/preferences/v1")
                                    .withJsonBodyContaining("{\"enableCharacterKeyShortcuts\": false}")
                                    .toSpyMatcher()));
                    play.click(play.getByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "Navigation")).toBeInTheDocument());
                    play.hover(menuItem(screen, "Navigation"));
                    play.waitFor(() -> play.expect(shortcuts(play)).toContain("Ctrl+E"));
                    play.hover(menuItem(screen, "User"));
                    play.waitFor(() -> play.expect(menuItem(screen, "Preferences")).toBeInTheDocument());
                    play.expect(shortcuts(play)).not().toContain("G U");
                    play.keyboard("{Escape}");
                    expectNoProblems(play);
                })
                // The maintenance banner above the shell
                .story("Banner", context -> render(context, TreeFixtures.fixtureTree(), routes -> {
                }, builder -> builder.uiConfig(QueryFixtures.uiConfigWith("\"maintenanceMessage\": "
                        + "\"System maintenance scheduled for 22:00 UTC — expect brief downtime.\"")), null))
                .withPlay(play -> {
                    // The banner is an alert, so a screen reader says it when it is shown
                    play.waitFor(() -> play.expect(play.querySelector(".mainViewImpl-banner"))
                            .toHaveTextContent(TextMatch.containingIgnoreCase("system maintenance")));
                    play.expect(play.getByRole("alert")).toHaveTextContent(
                            TextMatch.containingIgnoreCase("system maintenance"));
                    expectNoProblems(play);
                })
                // The current activity below the tree opens the chooser; choosing one updates it
                .story("ActivityChooser", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(ActivityFixtures.PATH,
                                RestReply.json(ActivityFixtures.page(ACTIVITIES))),
                        builder -> builder.uiConfig(ActivityFixtures.uiConfig(ACTIVITY_TITLE)), null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // GWT's summary is a button with no title
                    final Query summary = play.findByRole("button", TextMatch.startingWith("Current Activity"));
                    play.expect(play.within(summary).getByText("none")).toBeInTheDocument();
                    play.click(play.within(summary).getByText("Current Activity"));
                    screen.findByText(ACTIVITY_TITLE, StroomDom.DIALOG_TITLE);
                    play.click(screen.findByText(TextMatch.containing("INV-1002")));
                    play.click(screen.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(summary).toHaveTextContent(TextMatch.containing("INV-1002")));
                    // The chosen activity was made current
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
                    // Stroom asks for the activity before it shows the shell, so
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
                    // Once the warning is closed, Stroom closes the splash screen
                    // and never shows the app (SplashController hides it and passes the rejection on),
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
                    // GWT's empty tab pane shows nothing, so the play checks there are no tabs
                    play.expect(tabs(play)).toHaveLength(0);
                    play.dblClick(nav.getByText("Countries"));
                    play.expect(play.findByText("Words", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    play.expect(tabs(play)).toHaveLength(1);
                    // A folder opens the same way (FolderPlugin), with its Data tab.
                    // GWT's Data tab lists the folder's streams, so the play checks the folder's tab
                    // and its stream
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
                // The runs of icon buttons, and the tab stops each costs: the Main Menu and Hide
                // Sidebar buttons (each a toolbar of its own), the explorer's and the main toolbar's,
                // then those a document adds (its tab's Save buttons and its editor's)
                .story("ToolbarTabStops", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(navTree(play).getByText("Countries")).toBeInTheDocument());
                    play.expect(ButtonRuns.of(play.body())).toEqual(List.of(
                            "Main Menu: 1 tab stop",
                            "Hide Sidebar: 1 tab stop",
                            "Ask Stroom AI, Show Main Menu: 1 tab stop",
                            "Show Recent Filters, Quick Filter Syntax Help: 1 tab stop",
                            "New Document, Delete Selected Documents, Locate Current Item, Expand All Folders, "
                            + "Collapse All Folders, Filter Document Types, Find in Content: 1 tab stop"));
                    play.dblClick(navTree(play).getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save")).toBeInTheDocument());
                    play.expect(ButtonRuns.of(play.body())).toEqual(List.of(
                            "Main Menu: 1 tab stop",
                            "Hide Sidebar: 1 tab stop",
                            "Ask Stroom AI, Show Main Menu: 1 tab stop",
                            "Show Recent Filters, Quick Filter Syntax Help: 1 tab stop",
                            "Save, Save As, Download Dictionary Words: 1 tab stop",
                            "New Document, Delete Selected Documents, Locate Current Item, Expand All Folders, "
                            + "Collapse All Folders, Filter Document Types, Find in Content: 1 tab stop"));
                })
                // The main toolbar is shown at the right of the content's tab row, over the content,
                // but comes first in the page, so the Tab key reaches it before the explorer and the
                // content (it once came after all the content, so a keyboard user met it last)
                .story("MainToolbarTabOrder", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(navTree(play).getByText("Countries")).toBeInTheDocument());
                    final Query mainToolbar = play.querySelector(".main-toolbar");
                    final Query explorer = play.querySelector(NAVIGATION);
                    play.run("expect the main toolbar to come before the explorer in the page", () -> {
                        if (!isBefore(mainToolbar.element().get(), explorer.element().get())) {
                            throw new IllegalStateException("The main toolbar comes after the explorer");
                        }
                    });
                    // From the main menu's button, Tab goes on to the explorer
                    final Query mainMenu = play.getByTitle(MAIN_MENU);
                    play.run("focus the main menu's button", () -> mainMenu.element().get().focus());
                    play.tab();
                    play.expect(play.querySelector(NAVIGATION + ":focus-within")).toBeInTheDocument();
                    // The main view round it all isn't a tab stop (it was: a nameless stop holding
                    // the whole app, which Shift+Tab from the first button landed on)
                    play.expect(play.querySelector(".mainViewImpl[tabindex]")).not().toBeInTheDocument();
                })
                // #5405: a screen opened from the main menu takes the focus, rather than leave it
                // on the menu's button, so a keyboard user carries on in the screen they opened
                .story("OpenedScreenTakesFocus", context -> render(context, TreeFixtures.fixtureTree(),
                        routes -> routes.addAll(UserGroupsScreenStories.FIXTURES), builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "Security")).toBeInTheDocument());
                    play.hover(menuItem(screen, "Security"));
                    play.click(screen.findByText("User Groups", StroomDom.MENU_ITEM_TEXT));
                    play.findAllByText("Administrators");
                    play.waitFor(() -> play.expect(play.querySelector(CONTENT_WITH_FOCUS)).toBeInTheDocument());
                    play.expect(play.getByTitle(MAIN_MENU)).not().toHaveFocus();
                    expectNoProblems(play);
                })
                // #5403: the explorer and the content are landmarks (navigation and main) that screen
                // readers can go to, and F6 and Shift+F6 move the focus between the main toolbar, the
                // explorer and the content (the runner's real input check, test-runner/lib/real-input/
                // areas.mjs, presses them, as a play's F6 has no key code for the key bindings)
                .story("MovesBetweenAreas", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByRole("navigation", "Explorer")).toBeInTheDocument());
                    play.expect(play.getByRole("main")).toBeInTheDocument();
                    play.dblClick(navTree(play).getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.waitFor(() -> play.expect(play.querySelector(CONTENT_WITH_FOCUS)).toBeInTheDocument());
                })
                // #5405: a document opened from the explorer takes the focus
                .story("OpenedDocumentTakesFocus", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    play.dblClick(navTree(play).getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.waitFor(() -> play.expect(play.querySelector(CONTENT_WITH_FOCUS)).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // The 'Ask Stroom AI' chat docked to the right of the content (its config's dock
                // type is DOCK), opened with the toolbar's button: the main screen puts it in a split
                // pane of its own beside the content, whose splitter is named by the chat
                .story("AiDockedBesideContent", AppShellStories::renderDockedAi)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(navTree(play).getByText("Countries")).toBeInTheDocument());
                    play.click(play.getByTitle("Ask Stroom AI"));
                    final Query aiSplitter = play.findByRole("separator", "Ask Stroom AI");
                    play.expect(aiSplitter).toHaveAttribute("aria-orientation", "vertical");
                    // The explorer's splitter is still there, in the content beside the chat
                    play.expect(play.getByRole("separator", "Explorer")).toBeInTheDocument();
                    // Moving the chat's splitter saves its new size in the user's preferences
                    final Value<Integer> requests = play.spy(ScreenHarness.REQUEST_SPY).callCount();
                    final Value<Integer> before = play.capture("requests before", requests::get);
                    play.click(aiSplitter);
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenLastCalledWith(
                            RequestMatcher.post("/preferences/v1").withBodyContaining("dockSize").toSpyMatcher()));
                    play.expect("requests since", () -> requests.get() - before.get()).toBeGreaterThan(0);
                    expectNoProblems(play);
                })
                // Load, edit (dirty), save, and close with the unsaved changes confirmation
                .story("DictionaryLifecycle", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(navTree(play).getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    // Freshly loaded: Save is disabled
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save"))
                            .toHaveAttribute("aria-disabled", "true"));
                    // Remove an import (confirmed), which makes the document dirty
                    play.click(play.getByText("Imports", StroomDom.LINK_TAB_LABEL));
                    removeImport(play, "Alpha");
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save"))
                            .not().toHaveAttribute("aria-disabled"));
                    // Save: the server echoes, so the document is clean again
                    play.click(play.getByRole("button", "Save"));
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save"))
                            .toHaveAttribute("aria-disabled", "true"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(DICTIONARY_PATH + "dict-countries")
                                    .withJsonBodyContaining("{\"imports\": [{\"name\": \"Beta\"}]}")
                                    .toSpyMatcher());
                    // Edit again, then close: the unsaved changes are confirmed
                    removeImport(play, "Beta");
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save"))
                            .not().toHaveAttribute("aria-disabled"));
                    // The tab's close icon has no title
                    play.click(play.within(play.getByText(TextMatch.containing("Countries"), TAB_LABEL)
                                    .closest(".tab-bar__tab"))
                            .querySelector(".tab-bar__close"));
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
                    // name (it once kept the old label, as the document's tab wasn't refreshed when
                    // the name changed)
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
                    play.expect(screen.findByText("Copy to Clipboard", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    play.expect(screen.getByText("Add to Favourites", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    play.expect(screen.queryByText("Open", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    play.keyboard("{Escape}");
                    play.dblClick(nav.getByText("Countries"));
                    play.expect(play.findByText("Words", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // The System root isn't a document: Copy To..., Move, Rename and Delete are disabled
                .story("ContextMenuOnSystemRoot", context -> render(context, AppShellFixtures.rootedTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play, "System").getByText("System"));
                    play.expect(screen.findByText("Copy to Clipboard", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    for (final String name : List.of("Copy To...", "Move", "Rename", "Delete")) {
                        play.waitFor(() -> play.expect(menuItem(screen, name))
                                .toHaveAttribute("aria-disabled", "true"));
                    }
                    play.expect(menuItem(screen, "Edit Permissions")).not().toHaveAttribute("aria-disabled");
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
                    // The menu is shown on a secondary button's mouseup on a tab
                    play.fireEvent().mouseUp(play.getByText("Countries", TAB_LABEL), EventInit.create().button(2));
                    play.waitFor(() -> play.expect(menuItem(screen, "Close Other Tabs")).toBeInTheDocument());
                    for (final String name : List.of("Close", "Close Other Tabs", "Close Saved Tabs",
                            "Close All Tabs", "Close Tabs to the Left", "Close Tabs to the Right", "Save",
                            "Save All Tabs", "Move Tab to Start", "Move Tab to End", "Locate in Explorer",
                            "Add to Favourites")) {
                        play.expect(menuItem(screen, name)).toBeInTheDocument();
                    }
                    for (final String accelerator : List.of("Alt+W", "Alt+Shift+W", "Alt+Shift+S", "Ctrl+S",
                            "Ctrl+Shift+S", "Alt+L")) {
                        play.expect(shortcuts(play)).toContain(accelerator);
                    }
                    play.expect(menuItem(screen, "Close Tabs to the Left")).toHaveAttribute("aria-disabled", "true");
                    play.expect(menuItem(screen, "Close Tabs to the Right")).not().toHaveAttribute("aria-disabled");
                    play.expect(menuItem(screen, "Move Tab to Start")).toHaveAttribute("aria-disabled", "true");
                    play.expect(menuItem(screen, "Move Tab to End")).not().toHaveAttribute("aria-disabled");
                    play.expect(menuItem(screen, "Save")).toHaveAttribute("aria-disabled", "true");
                    play.expect(menuItem(screen, "Save All Tabs")).toHaveAttribute("aria-disabled", "true");
                    play.click(menuItem(screen, "Close Other Tabs"));
                    play.waitFor(() -> play.expect(tabs(play)).toHaveLength(1));
                    play.expect(tabs(play).nth(0)).toHaveTextContent(TextMatch.containing("Countries"));
                    expectNoProblems(play);
                })
                // #2278: a toolbar button that a shortcut also presses shows the shortcut in its
                // tooltip, and the User menu's Keyboard Shortcuts lists the shortcuts, which a filter
                // narrows. The runner's real input check (test-runner/lib/real-input/shortcuts.mjs)
                // presses '?' to show the list, and Alt+PageUp and Alt+PageDown to move between the
                // two tabs, as a play's keys have no key codes for the key bindings to read
                // G S opens Search Result Stores (it did nothing: the shortcut was bound to the
                // dialog before one had been made)
                .story("GoToSearchResults", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    navTree(play).findByText("Countries");
                    // GWT's key sequences complete on the keys' release, so the keys are pressed
                    // and released rather than only pressed
                    play.keyboard("gs");
                    play.waitFor(() -> play.expect(screen.getByText("Search Result Stores", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    expectNoProblems(play);
                })
                .story("KeyboardShortcuts", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    play.dblClick(nav.getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    // Save is still named 'Save'; its tooltip and aria-keyshortcuts give Ctrl+S
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveAttribute("title", "Save (Ctrl+S)");
                    play.expect(save).toHaveAttribute("aria-keyshortcuts", "Ctrl+S");
                    play.dblClick(nav.getByText("Colours"));
                    play.waitFor(5000, () -> play.expect(tabs(play)).toHaveLength(2));

                    play.click(play.findByTitle(MAIN_MENU));
                    play.waitFor(() -> play.expect(menuItem(screen, "User")).toBeInTheDocument());
                    play.hover(menuItem(screen, "User"));
                    play.waitFor(() -> play.expect(menuItem(screen, "Keyboard Shortcuts")).toBeInTheDocument());
                    play.expect(shortcuts(play)).toContain("Shift+/");
                    play.click(menuItem(screen, "Keyboard Shortcuts"));
                    final Play dialog = screen.within(screen.findByText("Keyboard Shortcuts", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.waitFor(() -> play.expect(dialog.getByText("Refresh list")).toBeInTheDocument());
                    play.expect(dialog.getByText("Alt+R")).toBeInTheDocument();
                    play.expect(dialog.getByText("OK, or run a query")).toBeInTheDocument();
                    // The filter takes the focus, and narrows the list to the matching shortcuts
                    final Query filter = dialog.getByLabelText("Filter keyboard shortcuts");
                    play.waitFor(() -> play.expect(filter).toHaveFocus());
                    play.type(filter, "alt+page");
                    play.waitFor(() -> play.expect(dialog.queryByText("Refresh list")).toBeNull());
                    play.expect(dialog.getByText("Go to next tab")).toBeInTheDocument();
                    play.expect(dialog.getByText("Go to previous tab")).toBeInTheDocument();
                    play.click(dialog.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText("Keyboard Shortcuts", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    expectNoProblems(play);
                })
                // #4293: in the explorer, F2 renames the item with the focus and Alt+Enter shows its
                // info, which its menu shows beside Rename and Show Info; a screen reader is told when
                // they can't (a feed can't be renamed). #4163: Alt+Shift+S closes the saved tabs. The
                // runner's real input check (test-runner/lib/real-input/shortcuts.mjs) presses the
                // keys, as a play's keys have no key codes for the key bindings to read
                .story("ExplorerItemKeys", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    rightClick(play, nav.getByText("Colours"));
                    play.waitFor(() -> play.expect(menuItem(screen, "Rename")).toBeInTheDocument());
                    play.expect(shortcuts(play)).toContain("F2");
                    play.expect(shortcuts(play)).toContain("Alt+Enter");
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByText("Rename", StroomDom.MENU_ITEM_TEXT)).toBeNull());
                    // Two saved tabs for Alt+Shift+S to close
                    play.dblClick(nav.getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.dblClick(nav.getByText("Colours"));
                    play.waitFor(5000, () -> play.expect(tabs(play)).toHaveLength(2));
                    expectNoProblems(play);
                })
                // From the keyboard: Shift+F10 or the context menu key opens a focused tab's menu
                // (below it), and Escape gives the focus back to the tab; Delete closes the focused
                // tab, and the focus moves to the tab before it
                .story("TabKeyboard", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    play.dblClick(nav.getByText("Countries"));
                    play.findByText("Words", StroomDom.LINK_TAB_LABEL);
                    play.dblClick(nav.getByText("Colours"));
                    play.waitFor(5000, () -> play.expect(tabs(play)).toHaveLength(2));
                    final Query countries = play.getByRole("tab", "Countries");
                    play.click(countries);
                    play.expect(countries).toHaveFocus();
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.waitFor(() -> play.expect(menuItem(screen, "Close Other Tabs")).toBeInTheDocument());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByText("Close Other Tabs", StroomDom.MENU_ITEM_TEXT))
                            .toBeNull());
                    play.waitFor(() -> play.expect(countries).toHaveFocus());
                    play.keyboard("{ContextMenu}");
                    play.waitFor(() -> play.expect(menuItem(screen, "Close Other Tabs")).toBeInTheDocument());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(countries).toHaveFocus());
                    play.keyboard("{ArrowRight}");
                    play.expect(play.getByRole("tab", "Colours")).toHaveFocus();
                    play.keyboard("{Delete}");
                    play.waitFor(() -> play.expect(tabs(play)).toHaveLength(1));
                    play.waitFor(() -> play.expect(countries).toHaveFocus());
                    expectNoProblems(play);
                })
                // No menu at all on the Favourites root
                .story("ContextMenuOnFavouritesRoot", context -> render(context, AppShellFixtures.rootedTree()))
                .withPlay(play -> {
                    rightClick(play, navTree(play, "System").getByText("Favourites"));
                    play.sleep(250);
                    play.expect(play.screen().queryByText("Copy to Clipboard", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    expectNoProblems(play);
                })
                // Info shows the document's identity, audit history and tags
                .story("InfoDialog", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.click(screen.findByText("Show Info", StroomDom.MENU_ITEM_TEXT));
                    // GWT bolds the key and appends the ': ' after it
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
                    // The quick filter has no label, only its placeholder
                    play.expect(play.within(dialog).findByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER))
                            .toBeInTheDocument();
                    final Query typeFilter = play.within(dialog).getByRole("button", "Filter Document Types");
                    play.expect(typeFilter).toHaveAttribute("aria-pressed", "false");
                    // The Filter Document Types button is beside the quick filter, in the same row
                    play.expect(play.within(typeFilter.closest(".dock-container-horizontal"))
                            .getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER)).toBeInTheDocument();
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
                    // the old label, as the document's tab wasn't refreshed when the name changed)
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
                // The toolbar's Delete Selected Documents is enabled by a selection, and deletes it
                .story("ToolbarDelete", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    final Query delete = nav.getByRole("button", "Delete Selected Documents");
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
                // New Document: the document types menu, the create dialog, and the new document opens
                .story("CreateDocument", context -> render(context, AppShellFixtures.dictTree(),
                        routes -> routes.get(StartupFixtures.DOCUMENT_TYPES_PATH, RestReply.json(DICTIONARY_TYPES))
                                .get(DICTIONARY_PATH + "new-doc", RestReply.json(newDoc("Regions"))),
                        builder -> {
                        }, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play nav = navTree(play);
                    final Query newButton = nav.getByRole("button", "New Document");
                    play.waitFor(() -> play.expect(newButton).toHaveAttribute("aria-disabled", "true"));
                    play.click(nav.getByText("Dictionaries"));
                    play.waitFor(() -> play.expect(newButton).not().toHaveAttribute("aria-disabled"));
                    // A menu button, which says whether its menu of document types is open
                    play.expect(newButton).toHaveAttribute("aria-haspopup", "menu");
                    play.expect(newButton).toHaveAttribute("aria-expanded", "false");
                    play.click(newButton);
                    play.waitFor(() -> play.expect(newButton).toHaveAttribute("aria-expanded", "true"));
                    play.click(screen.findByText("Dictionary", StroomDom.MENU_ITEM_TEXT));
                    play.waitFor(() -> play.expect(newButton).toHaveAttribute("aria-expanded", "false"));
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
                    // GWT's tree selects on mousedown, so Ctrl is held on the
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
                // Copy To... of one item: a name box filled with its name, and its folder chosen
                .story("CopyDocument", context -> render(context, AppShellFixtures.dictTree()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    rightClick(play, navTree(play).getByText("Countries"));
                    play.click(screen.findByText("Copy To...", StroomDom.MENU_ITEM_TEXT));
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
                    play.waitFor(() -> play.expect(menuItem(screen, "Delete"))
                            .toHaveAttribute("aria-disabled", "true"));
                    play.expect(menuItem(screen, "Rename")).toHaveAttribute("aria-disabled", "true");
                    play.expect(menuItem(screen, "Copy To...")).not().toHaveAttribute("aria-disabled");
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
                    play.expect(play.getByRole("button", "New Document")).toBeInTheDocument();
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
                    play.expect(shortcuts(play)).toContain("G W");
                    play.expect(shortcuts(play)).toContain("G Y");
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
                            .not().toHaveAttribute("aria-disabled"));
                    play.keyboard("{Escape}");
                    // The rule is Stroom's DocumentPluginRegistry.getExplorerDocRef,
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

    /// Renders the app shell over a tree, with the routes every app shell story answers.
    ///
    /// @param context The story's context.
    /// @param tree    The explorer's tree.
    /// @return The shell, started.
    static Widget render(final StoryContext context, final ExplorerFixture tree) {
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

    // The shell with the 'Ask Stroom AI' chat, configured to dock to the right of the content
    private static Widget renderDockedAi(final StoryContext context) {
        final ShellScreen shell = createShell(context, AppShellFixtures.dictTree(),
                routes -> AiFixtures.chatRoutes(routes, "{\"dockType\": \"DOCK\", \"dockLocation\": \"RIGHT\"}"),
                builder -> {
                });
        AskStroomAiChat.register(shell.getHarness(), shell.getInjector()::getAskStroomAiController);
        shell.start(null);
        return shell.asWidget();
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

    // What the shell asks for whatever the story: the explorer's tree, decorations and
    // permissions (the user owns every node and may create dictionaries), the explorer's
    // actions, the activities (none), and the Dictionary, Feed and Folder editors' data
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
                // The explorer's actions
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

    // Whether one element comes before another in the page (in document order)
    private static boolean isBefore(final Element first, final Element second) {
        final NodeList<Element> all = Document.get().getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            final Element element = all.getItem(i);
            if (element == second) {
                return false;
            }
            if (element == first) {
                return true;
            }
        }
        return false;
    }

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

    // Chooses the Stroom theme on the preferences' Theme tab
    private static void chooseTheme(final Play play, final Play dialog, final String theme) {
        play.click(dialog.getByText("Theme", StroomDom.LINK_TAB_LABEL));
        // The box is a combo box, which says when its list is open, and the list is named after it
        final Query box = dialog.querySelector("#userPreferencesTheme .selection-box__input");
        play.expect(box).toHaveAttribute("role", "combobox");
        play.expect(box).toHaveAttribute("aria-expanded", "false");
        play.click(box);
        play.waitFor(() -> play.expect(box).toHaveAttribute("aria-expanded", "true"));
        final Query list = play.screen().getByRole("listbox", "Theme");
        play.expect("the box controls the list", () -> list.element().get().getId()
                .equals(box.element().get().getAttribute("aria-controls"))).toEqual(true);
        // Its options, the current theme's selected
        play.waitFor(() -> play.expect(play.screen().within(list).getAllByRole("option").count())
                .toBeGreaterThan(1));
        play.click(play.screen().findByText(theme, ".selection-popup .selectionItemCell-text"));
        play.waitFor(() -> play.expect(box).toHaveAttribute("aria-expanded", "false"));
        play.waitFor(() -> play.expect(dialog.querySelector("#userPreferencesTheme .selection-box__value"))
                .toHaveTextContent(theme));
    }

    // Opens the Editor tab's Editor Theme list and checks it offers the themes, the first (Stroom's own)
    // first, then closes it
    private static void expectEditorThemes(final Play play, final Play dialog, final String... themes) {
        final Play screen = play.screen();
        play.click(dialog.getByText("Editor", StroomDom.LINK_TAB_LABEL));
        play.click(dialog.querySelector("#userPreferencesEditorTheme .selection-box__input"));
        final Play list = screen.within(screen.findByText(themes[0], ".selectionItemCell-text")
                .closest(".selection-popup"));
        play.expect(list.querySelector(".selectionItemCell-text")).toHaveTextContent(themes[0]);
        for (final String theme : themes) {
            play.expect(list.getByText(theme, ".selectionItemCell-text")).toBeInTheDocument();
        }
        play.keyboard("{Escape}");
        play.waitFor(() -> play.expect(screen.querySelector(".selection-popup")).toBeNull());
    }

    // Answers the save of the user's preferences
    private static void savePreferencesRoute(final RestFixtures.Builder routes) {
        routes.post(StartupFixtures.USER_PREFERENCES_PATH, RestReply.json("true"));
    }

    // Opens the preferences with 'g' then 'u', and their Keyboard Shortcuts tab
    private static Play openKeyboardShortcuts(final Play play) {
        final Play screen = play.screen();
        play.findByText("Dictionaries");
        play.keyboard("gu");
        final Play dialog = screen.within(screen.findByText("User Preferences", StroomDom.DIALOG_TITLE)
                .closest(StroomDom.DIALOG));
        play.click(dialog.getByText("Keyboard Shortcuts", StroomDom.LINK_TAB_LABEL));
        play.waitFor(() -> play.expect(dialog.getByText("Recent items")).toBeInTheDocument());
        return dialog;
    }

    // Opens the recorder for the action's (first) shortcut, by double-clicking its row
    private static Play recordShortcut(final Play play, final Play dialog, final String action) {
        final Play screen = play.screen();
        play.dblClick(dialog.getAllByText(action).first());
        return screen.within(screen.findByText("Change Keyboard Shortcut", StroomDom.DIALOG_TITLE)
                .closest(StroomDom.DIALOG));
    }

    private static Query menuItem(final Play screen, final String title) {
        return screen.querySelector(MENU_ITEM_ROLE + ":has(> " + MENU_ITEM + "[title=\"" + title + "\"])");
    }

    private static Supplier<List<String>> shortcuts(final Play play) {
        return play.screen().querySelectorAll(MENU_SHORTCUT).textContents();
    }

    // The explorer's menu is shown on a secondary button's mousedown, not a
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
        play.click(play.getByTitle("Remove Imported Dictionary"));
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
