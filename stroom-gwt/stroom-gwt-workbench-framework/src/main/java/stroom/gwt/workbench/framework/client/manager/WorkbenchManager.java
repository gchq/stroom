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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.BrowserUtil;
import stroom.gwt.workbench.framework.client.WorkbenchResources;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.args.ArgsCodec;
import stroom.gwt.workbench.framework.client.manager.addons.AccessibilityAddon;
import stroom.gwt.workbench.framework.client.manager.addons.ActionsAddon;
import stroom.gwt.workbench.framework.client.manager.addons.ControlsAddon;
import stroom.gwt.workbench.framework.client.manager.addons.InteractionsAddon;
import stroom.gwt.workbench.framework.client.preview.StoryPreview;
import stroom.gwt.workbench.framework.client.shortcuts.KeyCombo;
import stroom.gwt.workbench.framework.client.shortcuts.ShortcutAction;
import stroom.gwt.workbench.framework.client.story.Story;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.gwt.workbench.framework.client.story.StoryUrls;
import stroom.gwt.workbench.framework.client.tree.SidebarModel;
import stroom.gwt.workbench.framework.client.tree.StoryNavigation;
import stroom.gwt.workbench.framework.client.tree.StoryTreeBuilder;
import stroom.gwt.workbench.framework.client.tree.StoryTreeNode;
import stroom.gwt.workbench.framework.client.tree.TagFilter;

import com.google.gwt.dom.client.BodyElement;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/// The workbench 'manager', i.e. the page around the preview iframe (index.html). It adds the
/// sidebar, toolbar and addon panel to the page, wires them together and keeps the URL in sync
/// with the selected story or settings page.
public class WorkbenchManager {

    private static final String BODY_CLASS = "wbm";
    private static final String IFRAME_ID = "workbench-preview-iframe";
    private static final String TITLE_SUFFIX = " ⋅ Stroom GWT Workbench";
    private static final String ADDON_PANEL_PARAM = "addonPanel";
    private static final String A11Y_PANEL = "workbench/a11y/panel";
    private static final String A11Y_SELECTION_PARAM = "a11ySelection";
    private static final int A11Y_ADDON_INDEX = 3;
    private static final String OPEN_IN_EDITOR_URL = "__workbench/api/open-in-editor";
    private static final String SEARCH_SHORTCUT_ID = "wbm-search-shortcut";
    private static final String CHANGES_URL = "__workbench/api/changes?classes=";

    private final StoryRegistry registry;
    private final SidebarModel sidebarModel;
    // Rebuilt whenever the tag filter changes, so only the stories in the sidebar are visited
    private StoryNavigation navigation;
    private final LayoutController layout;
    private final ShortcutHandler shortcutHandler;
    private final SidebarView sidebarView;
    private final SettingsMenu settingsMenu;
    private final SettingsPages settingsPages;
    private final AddonPanel addonPanel;
    private final ControlsAddon controlsAddon;
    private final ActionsAddon actionsAddon;
    private final InteractionsAddon interactionsAddon;
    private final AccessibilityAddon accessibilityAddon;
    // The args the user has changed, by story id, kept while the page is open as in Storybook
    private final Map<String, Args> changedArgs = new HashMap<>();
    private final IFrameElement iframe;
    private final PreviewTools previewTools;
    private final TagFilter tagFilter = new TagFilter();
    private final CreateStoryDialog createStoryDialog = new CreateStoryDialog();
    // The git change status (new, modified or related) of the stories' classes
    private final Map<String, String> statusByClass = new HashMap<>();

    /// @param registry All the stories.
    public WorkbenchManager(final StoryRegistry registry) {
        this.registry = registry;
        addManagerToPage();

        final List<StoryTreeNode> topLevelNodes = StoryTreeBuilder.build(registry.getStories());
        sidebarModel = new SidebarModel(topLevelNodes);
        navigation = new StoryNavigation(topLevelNodes);
        layout = new LayoutController();
        shortcutHandler = new ShortcutHandler();
        sidebarView = new SidebarView(sidebarModel, topLevelNodes, shortcutHandler,
                new SidebarView.Listener() {
                    @Override
                    public void onStorySelected(final String storyId) {
                        navigateToStory(storyId);
                    }

                    @Override
                    public void onOpenInEditor(final StoryTreeNode node) {
                        openInEditor(node.getId());
                    }
                });
        sidebarView.setStatusProvider(this::getStatus);
        new TagFilterMenu(tagFilter, this::getTagCounts, this::applyTagFilter);
        settingsMenu = new SettingsMenu(shortcutHandler, layout);
        settingsPages = new SettingsPages(shortcutHandler, this::navigateToSettings);
        iframe = Document.get().getElementById(IFRAME_ID).cast();
        controlsAddon = new ControlsAddon(this::onArgsChanged);
        actionsAddon = new ActionsAddon();
        interactionsAddon = new InteractionsAddon(
                control -> BrowserUtil.postToPreview(iframe, BrowserUtil.PLAY_CONTROL_MESSAGE, control.name()),
                () -> openInEditor(sidebarModel.getSelectedId()));
        accessibilityAddon = new AccessibilityAddon(new AccessibilityAddon.Listener() {
            @Override
            public void onRerun() {
                BrowserUtil.postToPreview(iframe, BrowserUtil.A11Y_RUN_MESSAGE, (String) null);
            }

            @Override
            public void onHighlight(final String itemsJson) {
                BrowserUtil.postToPreview(iframe, BrowserUtil.A11Y_HIGHLIGHT_MESSAGE, itemsJson);
            }

            @Override
            public void onJumpTo(final String selector, final String colour) {
                BrowserUtil.postToPreview(iframe, BrowserUtil.A11Y_JUMP_MESSAGE, selector, colour);
            }

            @Override
            public void onCopyLink(final String resultKey) {
                final String withPanel = UrlParams.withParameter(BrowserUtil.getHref(), ADDON_PANEL_PARAM, A11Y_PANEL);
                BrowserUtil.copyToClipboard(UrlParams.withParameter(withPanel, A11Y_SELECTION_PARAM, resultKey));
            }
        });
        addonPanel = new AddonPanel(List.of(controlsAddon, actionsAddon, interactionsAddon, accessibilityAddon));
        previewTools = new PreviewTools(iframe, shortcutHandler);

        layout.setChangeHandler(this::onLayoutChange);
    }

    /// Adds the manager's styles and markup to the page so the views can find their elements.
    private static void addManagerToPage() {
        StyleInjector.inject(WorkbenchResources.INSTANCE.managerCss().getText(), true);
        final BodyElement body = Document.get().getBody();
        body.addClassName(BODY_CLASS);
        final DivElement container = Document.get().createDivElement();
        container.setInnerSafeHtml(SafeHtmlUtils.fromTrustedString(
                WorkbenchResources.INSTANCE.managerHtml().getText()
                // The vision filters are needed for the swatches in the vision filter menu
                + WorkbenchResources.INSTANCE.visionFiltersSvg().getText()));
        body.appendChild(container);
    }

    /// Shows the story (or settings page) in the URL and starts responding to the user.
    public void start() {
        bindToolbar();
        previewTools.bind();
        bindPanelButtons();
        bindShortcuts();
        BrowserUtil.addPopStateHandler(() -> showLocation(false));
        // Only messages from the preview iframe are accepted
        BrowserUtil.addMessageHandler(iframe, (type, name, detail, value) -> {
            if (BrowserUtil.ACTION_MESSAGE.equals(type)) {
                actionsAddon.log(name, detail);
            } else if (BrowserUtil.KEYDOWN_MESSAGE.equals(type) && name != null) {
                // A shortcut pressed while the canvas has the focus
                shortcutHandler.handle(KeyCombo.parse(name), detail != null);
            } else if (BrowserUtil.INTERACTIONS_MESSAGE.equals(type)
                       && name != null && name.equals(sidebarModel.getSelectedId())) {
                interactionsAddon.update(detail);
            } else if (BrowserUtil.A11Y_RESULT_MESSAGE.equals(type)) {
                accessibilityAddon.update(name, detail);
            }
        });
        final String a11ySelection = BrowserUtil.getQueryParameter(A11Y_SELECTION_PARAM);
        if (a11ySelection != null) {
            addonPanel.select(A11Y_ADDON_INDEX);
        }
        shortcutHandler.addChangeHandler(this::updateSearchHint);
        updateSearchHint();
        showLocation(true);
        // After showing the story, which resets the addon, so the result is expanded once the
        // checks have run
        if (a11ySelection != null) {
            accessibilityAddon.selectResult(a11ySelection);
        }
        sidebarView.scrollToSelected();
        fetchChanges();
    }

    /// Shows the current 'Focus search' shortcut in the search box, as Storybook does.
    private void updateSearchHint() {
        final Element hint = Document.get().getElementById(SEARCH_SHORTCUT_ID);
        if (hint == null) {
            return;
        }
        final KeyCombo combo = shortcutHandler.getShortcuts().get(ShortcutAction.SEARCH);
        if (combo != null) {
            hint.setInnerText(combo.toDisplayString());
            hint.getStyle().clearDisplay();
        } else {
            hint.setInnerText("");
            hint.getStyle().setDisplay(Display.NONE);
        }
    }

    // ---------- Navigation ----------

    /// Shows whatever the current URL points at.
    private void showLocation(final boolean replaceUrl) {
        final String path = BrowserUtil.getQueryParameter(StoryUrls.PATH_PARAM);
        final String settingsPage = StoryUrls.settingsPageFromPath(path);
        if (settingsPage != null) {
            showSettings(settingsPage);
        } else {
            final String storyId = getStoryId(StoryUrls.storyIdFromPath(path));
            final Story story = registry.getStory(storyId);
            if (story != null) {
                // Args in the URL, e.g. from a shared link
                final Args urlArgs = StoryPreview.typedArgs(story,
                        ArgsCodec.decode(BrowserUtil.getQueryParameter(StoryUrls.ARGS_PARAM)));
                setChangedArgs(storyId, urlArgs);
            }
            showStory(storyId, replaceUrl);
        }
    }

    private void setChangedArgs(final String storyId, final Args args) {
        if (args.size() == 0) {
            changedArgs.remove(storyId);
        } else {
            changedArgs.put(storyId, args);
        }
    }

    private String getEncodedArgs(final String storyId) {
        final Args args = changedArgs.get(storyId);
        return args != null
                ? ArgsCodec.encode(args)
                : null;
    }

    /// Called when the user changes an arg in the Controls addon.
    private void onArgsChanged(final Args args) {
        final String storyId = sidebarModel.getSelectedId();
        final Story story = registry.getStory(storyId);
        if (story == null) {
            return;
        }
        setChangedArgs(storyId, args.diff(story.getInitialArgs()));
        final String encoded = getEncodedArgs(storyId);
        // As in Storybook, the URL holds the changed args so the story can be shared
        BrowserUtil.setUrl(StoryUrls.managerUrl(storyId, encoded), true);
        BrowserUtil.postToPreview(iframe, BrowserUtil.ARGS_MESSAGE, encoded != null
                ? encoded
                : "");
        accessibilityAddon.reset();
    }

    private String getStoryId(final String storyId) {
        if (registry.getStory(storyId) != null) {
            return storyId;
        }
        final Story first = registry.getFirstStory();
        return first != null
                ? first.getId()
                : null;
    }

    private void navigateToStory(final String storyId) {
        if (storyId == null) {
            return;
        }
        showStory(storyId, false);
        BrowserUtil.setUrl(StoryUrls.managerUrl(storyId, getEncodedArgs(storyId)), false);
    }

    private void navigateToSettings(final String page) {
        if (page == null) {
            // Closing the settings returns to the last story
            navigateToStory(getStoryId(sidebarModel.getSelectedId()));
            return;
        }
        showSettings(page);
        BrowserUtil.setUrl(StoryUrls.settingsUrl(page), false);
    }

    private void showStory(final String storyId, final boolean replaceUrl) {
        final Story story = registry.getStory(storyId);
        if (story == null) {
            return;
        }
        final String encodedArgs = getEncodedArgs(storyId);
        if (replaceUrl) {
            BrowserUtil.setUrl(StoryUrls.managerUrl(storyId, encodedArgs), true);
        }
        layout.setSettingsVisible(false);
        settingsPages.hide();
        final boolean changed = !storyId.equals(sidebarModel.getSelectedId()) || !iframe.hasAttribute("src");
        sidebarModel.select(storyId);
        sidebarView.render();
        if (changed) {
            final Args currentArgs = story.getInitialArgs().merge(changedArgs.containsKey(storyId)
                    ? changedArgs.get(storyId)
                    : Args.empty());
            controlsAddon.setStory(story, currentArgs);
            actionsAddon.clear();
            interactionsAddon.reset(getSourceFileName(story));
            accessibilityAddon.reset();
            BrowserUtil.replaceLocation(iframe, StoryUrls.previewUrl(storyId, encodedArgs));
        }
        Document.get().setTitle(story.getTitle().replace("/", " / ")
                                + " - " + story.getName() + TITLE_SUFFIX);
    }

    private void showSettings(final String page) {
        Popover.hideCurrent();
        settingsPages.show(page);
        layout.setSettingsVisible(true);
        Document.get().setTitle("Stroom GWT Workbench");
    }

    private static String getSourceFileName(final Story story) {
        final String className = story.getSourceClassName();
        if (className == null) {
            return null;
        }
        return className.substring(className.lastIndexOf('.') + 1) + ".java";
    }

    /// Asks the workbench server to open the Java source of a story, or of the first story below
    /// a component, in an editor.
    private void openInEditor(final String nodeId) {
        // The sidebar only has the stories that match the tag filter, so a story that is
        // filtered out (e.g. the selected one) is found in the registry
        final StoryTreeNode node = sidebarModel.getNode(nodeId);
        final Story story = node != null
                ? findFirstStory(node)
                : registry.getStory(nodeId);
        if (story == null || story.getSourceClassName() == null) {
            Notifications.show("Unable to open in editor", "The story's source class isn't known.", true);
            return;
        }
        final String url = OPEN_IN_EDITOR_URL
                           + "?class=" + BrowserUtil.encodeQueryParam(story.getSourceClassName())
                           + "&id=" + BrowserUtil.encodeQueryParam(story.getId());
        BrowserUtil.request("POST", url, (status, body) -> {
            if (status != 200) {
                Notifications.show("Unable to open in editor", CreateStoryDialog.errorMessage(status, body), true);
            }
        });
    }

    private static Story findFirstStory(final StoryTreeNode node) {
        if (node.getStory() != null) {
            return node.getStory();
        }
        for (final StoryTreeNode child : node.getChildren()) {
            final Story story = findFirstStory(child);
            if (story != null) {
                return story;
            }
        }
        return null;
    }

    // ---------- Tags and changes ----------

    /// @return The story's tags plus its git change status, which can also be filtered on.
    private Set<String> getTags(final Story story) {
        final String status = statusByClass.get(story.getSourceClassName());
        if (status == null) {
            return story.getTags();
        }
        final Set<String> tags = new LinkedHashSet<>(story.getTags());
        tags.add(status);
        return tags;
    }

    private Map<String, Integer> getTagCounts() {
        final Map<String, Integer> counts = new TreeMap<>();
        for (final Story story : registry.getStories()) {
            for (final String tag : getTags(story)) {
                counts.merge(tag, 1, Integer::sum);
            }
        }
        return counts;
    }

    private String getStatus(final StoryTreeNode node) {
        return node.getStory() != null
                ? statusByClass.get(node.getStory().getSourceClassName())
                : null;
    }

    /// Rebuilds the sidebar tree from the stories that match the tag filter.
    private void applyTagFilter() {
        final List<Story> stories = new ArrayList<>();
        for (final Story story : registry.getStories()) {
            if (tagFilter.matches(getTags(story))) {
                stories.add(story);
            }
        }
        final List<StoryTreeNode> nodes = StoryTreeBuilder.build(stories);
        sidebarModel.setTopLevelNodes(nodes);
        navigation = new StoryNavigation(nodes);
        sidebarView.setTree(nodes);
    }

    /// Asks the workbench server which of the stories' classes have changed in git, so they
    /// can be marked in the sidebar and filtered on, as Storybook's change detection does.
    private void fetchChanges() {
        final Set<String> classNames = new TreeSet<>();
        for (final Story story : registry.getStories()) {
            if (story.getSourceClassName() != null) {
                classNames.add(story.getSourceClassName());
            }
        }
        if (classNames.isEmpty()) {
            return;
        }
        // The classes go in the URL, so are sent in batches to keep each URL short
        statusByClass.clear();
        for (final List<String> batch : ChangeRequests.batch(classNames, ChangeRequests.MAX_CLASSES_PER_REQUEST)) {
            final String url = CHANGES_URL + BrowserUtil.encodeQueryParam(String.join(",", batch));
            BrowserUtil.request("GET", url, (status, body) -> {
                if (status != 200) {
                    // Without the server's API, e.g. when served statically, there is no change
                    // detection
                    return;
                }
                final JSONObject changes = parseObject(body);
                if (changes == null) {
                    return;
                }
                addStatuses(changes, TagFilter.RELATED);
                addStatuses(changes, TagFilter.MODIFIED);
                addStatuses(changes, TagFilter.NEW);
                applyTagFilter();
            });
        }
    }

    /// @return The JSON object in a response body, or null if the body isn't a JSON object.
    private static JSONObject parseObject(final String body) {
        if (body == null) {
            return null;
        }
        try {
            final JSONValue value = JSONParser.parseStrict(body);
            return value != null
                    ? value.isObject()
                    : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private void addStatuses(final JSONObject changes, final String status) {
        final JSONValue value = changes.get(status);
        final JSONArray classNames = value != null
                ? value.isArray()
                : null;
        if (classNames == null) {
            return;
        }
        for (int i = 0; i < classNames.size(); i++) {
            final JSONValue className = classNames.get(i);
            if (className != null && className.isString() != null) {
                ChangeRequests.putStatus(statusByClass, className.isString().stringValue(), status);
            }
        }
    }

    // ---------- Toolbar and panel ----------

    private void bindToolbar() {
        onClick("wbm-reload", this::remount);
        onClick("wbm-isolation", this::openInIsolation);
        onClick("wbm-fullscreen", layout::toggleFullscreen);
        onClick("wbm-open-in-editor", () -> openInEditor(sidebarModel.getSelectedId()));
        onClick("wbm-show-sidebar", layout::toggleSidebar);
        onClick("wbm-show-panel", layout::togglePanel);
        onClick("wbm-create-button", createStoryDialog::show);
    }

    private void bindPanelButtons() {
        onClick("wbm-panel-position", layout::togglePanelPosition);
        onClick("wbm-panel-hide", layout::hidePanel);
    }

    private void onLayoutChange() {
        final boolean right = layout.isPanelRight();
        updateButton("wbm-panel-position",
                right
                        ? "Move addon panel to bottom"
                        : "Move addon panel to right",
                right
                        ? "wbm-icon-panel-bottom"
                        : "wbm-icon-panel-right");
        updateButton("wbm-fullscreen",
                layout.isFullscreen()
                        ? "Exit full screen"
                        : "Enter full screen",
                layout.isFullscreen()
                        ? "wbm-icon-close-panel"
                        : "wbm-icon-fullscreen");
        updateButton("wbm-show-panel",
                "Show addon panel",
                right
                        ? "wbm-icon-panel-right"
                        : "wbm-icon-panel-bottom");
        // The menu is created after the layout so may not exist yet
        if (settingsMenu != null) {
            settingsMenu.refresh();
        }
    }

    private static void updateButton(final String buttonId, final String label, final String icon) {
        final Element button = Document.get().getElementById(buttonId);
        if (button != null) {
            button.setAttribute("aria-label", label);
            button.setTitle(label);
            button.getElementsByTagName("use").getItem(0).setAttribute("href", "#" + icon);
        }
    }

    private void remount() {
        BrowserUtil.reload(iframe);
    }

    private void openInIsolation() {
        final String storyId = sidebarModel.getSelectedId();
        if (storyId != null) {
            Window.open(StoryUrls.previewUrl(storyId), "_blank", "");
        }
    }

    // ---------- Keyboard shortcuts ----------

    private void bindShortcuts() {
        shortcutHandler.register(ShortcutAction.FULL_SCREEN, layout::toggleFullscreen);
        shortcutHandler.register(ShortcutAction.TOGGLE_PANEL, layout::togglePanel);
        shortcutHandler.register(ShortcutAction.PANEL_POSITION, layout::togglePanelPosition);
        shortcutHandler.register(ShortcutAction.TOGGLE_NAV, layout::toggleSidebar);
        shortcutHandler.register(ShortcutAction.TOOLBAR, layout::toggleToolbar);
        shortcutHandler.register(ShortcutAction.SEARCH, () -> {
            showSidebarIfHidden();
            sidebarView.focusSearch();
        });
        shortcutHandler.register(ShortcutAction.FOCUS_NAV, () -> {
            showSidebarIfHidden();
            sidebarView.focusTree();
        });
        shortcutHandler.register(ShortcutAction.FOCUS_IFRAME, () -> BrowserUtil.focusIframe(iframe));
        shortcutHandler.register(ShortcutAction.FOCUS_PANEL, addonPanel::focus);
        shortcutHandler.register(ShortcutAction.PREV_COMPONENT, () -> navigateAdjacent(true, false));
        shortcutHandler.register(ShortcutAction.NEXT_COMPONENT, () -> navigateAdjacent(true, true));
        shortcutHandler.register(ShortcutAction.PREV_STORY, () -> navigateAdjacent(false, false));
        shortcutHandler.register(ShortcutAction.NEXT_STORY, () -> navigateAdjacent(false, true));
        shortcutHandler.register(ShortcutAction.SHORTCUTS_PAGE,
                () -> navigateToSettings(SettingsPages.SHORTCUTS));
        shortcutHandler.register(ShortcutAction.ABOUT_PAGE, () -> navigateToSettings(SettingsPages.ABOUT));
        shortcutHandler.register(ShortcutAction.COLLAPSE_ALL, () -> sidebarView.setAllExpanded(false));
        shortcutHandler.register(ShortcutAction.EXPAND_ALL, () -> sidebarView.setAllExpanded(true));
        shortcutHandler.register(ShortcutAction.REMOUNT, this::remount);
        shortcutHandler.register(ShortcutAction.OPEN_IN_EDITOR,
                () -> openInEditor(sidebarModel.getSelectedId()));
        shortcutHandler.register(ShortcutAction.OPEN_IN_ISOLATION, this::openInIsolation);
        shortcutHandler.register(ShortcutAction.COPY_STORY_LINK,
                () -> BrowserUtil.copyToClipboard(BrowserUtil.getHref()));
        shortcutHandler.register(ShortcutAction.PREVIOUS_LANDMARK, () -> Landmarks.focusNext(false));
        shortcutHandler.register(ShortcutAction.NEXT_LANDMARK, () -> Landmarks.focusNext(true));
    }

    private void navigateAdjacent(final boolean component, final boolean forwards) {
        final String current = sidebarModel.getSelectedId();
        navigateToStory(component
                ? navigation.adjacentComponent(current, forwards)
                : navigation.adjacentStory(current, forwards));
    }

    private void showSidebarIfHidden() {
        if (!layout.isSidebarVisible()) {
            layout.toggleSidebar();
        }
    }

    // ---------- Helpers ----------

    private static void onClick(final String elementId, final Runnable action) {
        final Element element = Document.get().getElementById(elementId);
        if (element != null) {
            BrowserUtil.addListener(element, "click", event -> action.run());
        }
    }
}
