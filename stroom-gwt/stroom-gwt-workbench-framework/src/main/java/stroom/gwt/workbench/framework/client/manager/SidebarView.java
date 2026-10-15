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
import stroom.gwt.workbench.framework.client.shortcuts.ShortcutAction;
import stroom.gwt.workbench.framework.client.story.StoryUrls;
import stroom.gwt.workbench.framework.client.tree.SidebarModel;
import stroom.gwt.workbench.framework.client.tree.StorySearch;
import stroom.gwt.workbench.framework.client.tree.StorySearch.SearchResult;
import stroom.gwt.workbench.framework.client.tree.StoryTreeNode;
import stroom.gwt.workbench.framework.client.tree.StoryTreeNode.Type;
import stroom.gwt.workbench.framework.client.tree.TagFilter;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/// Renders the sidebar tree (or the search results) and handles clicks, typing and keyboard
/// navigation in the sidebar. The markup and class names mirror React Storybook's sidebar,
/// styled by workbench-manager.css.
public class SidebarView {

    private static final String MENU_ID = "workbench-explorer-menu";
    private static final String TREE_ID = "workbench-explorer-tree";
    private static final String SEARCH_INPUT_ID = "workbench-explorer-searchfield";
    private static final String SEARCH_FIELD_ID = "wbm-search-field";
    private static final String SEARCH_CLEAR_ID = "wbm-search-clear";

    private static final String ATTR_ITEM_ID = "data-item-id";
    private static final String ATTR_ACTION = "data-action";
    private static final String ACTION_TOGGLE = "toggle";
    private static final String ACTION_EXPAND_ALL = "expand-all";
    private static final String ACTION_CONTEXT_MENU = "context-menu";
    private static final String MENU_OPEN_IN_EDITOR = "open-in-editor";
    private static final String MENU_COPY_NAME = "copy-name";

    private static final String PLACEHOLDER = "Find components";
    private static final String FOCUSED_PLACEHOLDER = "Type to find...";

    // Indentation, matching Storybook
    private static final int BASE_INDENT_PX = 8;
    private static final int LEAF_EXTRA_INDENT_PX = 14;
    private static final int INDENT_PER_LEVEL_PX = 18;

    private final SidebarModel model;
    private List<StoryTreeNode> topLevelNodes;
    private final Listener listener;
    private final ShortcutHandler shortcutHandler;
    private final Element menuElement;
    private final Element treeElement;
    private final InputElement searchInput;
    private final Element searchField;
    private String query = "";
    private List<SearchResult> results = new ArrayList<>();
    // The item highlighted by keyboard navigation, in the tree or the search results
    private String highlightedId;
    // Gives the git status tag (new/modified/related) of a story, or null
    private Function<StoryTreeNode, String> statusProvider = node -> null;

    /// @param model           The tree state.
    /// @param topLevelNodes   The top level nodes of the tree.
    /// @param shortcutHandler The keyboard shortcuts, to show in the context menu.
    /// @param listener        Told when the user selects a story or uses the context menu.
    public SidebarView(final SidebarModel model,
                       final List<StoryTreeNode> topLevelNodes,
                       final ShortcutHandler shortcutHandler,
                       final Listener listener) {
        this.model = model;
        this.topLevelNodes = topLevelNodes;
        this.shortcutHandler = shortcutHandler;
        this.listener = listener;

        final Document document = Document.get();
        menuElement = document.getElementById(MENU_ID);
        treeElement = document.getElementById(TREE_ID);
        searchInput = document.getElementById(SEARCH_INPUT_ID).cast();
        searchField = document.getElementById(SEARCH_FIELD_ID);

        BrowserUtil.addListener(treeElement, "click", this::onTreeClick);
        BrowserUtil.addListener(menuElement, "keydown", this::onTreeKeyDown);
        BrowserUtil.addListener(menuElement, "focus", event -> {
            if (highlightedId == null) {
                highlightedId = model.getSelectedId();
            }
            render();
        });
        BrowserUtil.addListener(searchInput, "input", event -> setQuery(searchInput.getValue()));
        BrowserUtil.addListener(searchInput, "keydown", this::onSearchKeyDown);
        BrowserUtil.addListener(searchInput, "focus", event -> setSearchFocused(true));
        BrowserUtil.addListener(searchInput, "blur", event -> setSearchFocused(false));
        BrowserUtil.addListener(document.getElementById(SEARCH_CLEAR_ID), "click", event -> clearSearch());
    }

    /// Replaces the tree, e.g. when the tag filter changes. The model must already have the new
    /// tree.
    ///
    /// @param topLevelNodes The new top level nodes.
    public void setTree(final List<StoryTreeNode> topLevelNodes) {
        this.topLevelNodes = topLevelNodes;
        results = StorySearch.search(topLevelNodes, query);
        if (model.getNode(highlightedId) == null) {
            highlightedId = null;
        }
        render();
    }

    /// @param statusProvider Gives the change status of a story, i.e. one of
    ///                       [stroom.gwt.workbench.framework.client.tree.TagFilter#NEW],
    ///                       [stroom.gwt.workbench.framework.client.tree.TagFilter#MODIFIED] or
    ///                       [stroom.gwt.workbench.framework.client.tree.TagFilter#RELATED], or null if
    ///                       the story hasn't changed.
    public void setStatusProvider(final Function<StoryTreeNode, String> statusProvider) {
        this.statusProvider = statusProvider;
        render();
    }

    /// Re-renders the sidebar from the current state of the model.
    public void render() {
        if (query.isEmpty()) {
            treeElement.setInnerSafeHtml(renderTree());
        } else {
            treeElement.setInnerSafeHtml(renderSearchResults());
        }
        if (highlightedId != null) {
            BrowserUtil.scrollIntoViewIfNeeded(findItem(highlightedId));
        }
    }

    /// Scrolls the sidebar so the selected story is visible.
    public void scrollToSelected() {
        final String selectedId = model.getSelectedId();
        if (selectedId != null) {
            BrowserUtil.scrollIntoViewIfNeeded(findItem(selectedId));
        }
    }

    /// Moves the keyboard focus to the search box.
    public void focusSearch() {
        BrowserUtil.focus(searchInput);
        searchInput.select();
    }

    /// Moves the keyboard focus to the tree, highlighting the selected story.
    public void focusTree() {
        highlightedId = model.getSelectedId();
        BrowserUtil.focus(menuElement);
        render();
    }

    /// Expands or collapses every node in the tree.
    ///
    /// @param expanded True to expand all, false to collapse all.
    public void setAllExpanded(final boolean expanded) {
        for (final StoryTreeNode node : topLevelNodes) {
            if (node.getType() == Type.ROOT) {
                model.setAllExpanded(node.getId(), expanded);
            } else if (node.isExpandable()) {
                model.setExpanded(node.getId(), expanded);
                model.setAllExpanded(node.getId(), expanded);
            }
        }
        render();
    }

    private Element findItem(final String itemId) {
        return Document.get().getElementById(itemElementId(itemId));
    }

    private static String itemElementId(final String itemId) {
        return "wbm-item-" + itemId;
    }

    // ---------- Rendering ----------

    private SafeHtml renderTree() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (topLevelNodes.isEmpty()) {
            builder.appendHtmlConstant("<div class=\"wbm-no-results\"><strong>No stories found</strong>"
                                       + "<small>No stories match the tag filters.</small></div>");
            return builder.toSafeHtml();
        }
        for (final StoryTreeNode node : model.getVisibleNodes()) {
            if (node.getType() == Type.ROOT) {
                renderRoot(builder, node);
            } else {
                renderItem(builder, node);
            }
        }
        return builder.toSafeHtml();
    }

    private void renderRoot(final SafeHtmlBuilder builder, final StoryTreeNode node) {
        final boolean expanded = model.isExpanded(node.getId());
        final boolean allExpanded = model.isAllExpanded(node.getId());

        builder.appendHtmlConstant("<div class=\"wbm-root\" data-nodetype=\"root\" "
                                   + ATTR_ITEM_ID + "=\"").appendEscaped(node.getId())
                .appendHtmlConstant("\" id=\"").appendEscaped(itemElementId(node.getId()))
                .appendHtmlConstant("\">");

        builder.appendHtmlConstant("<button type=\"button\" tabindex=\"-1\" class=\"wbm-root__toggle\" "
                                   + ATTR_ACTION + "=\"" + ACTION_TOGGLE + "\" aria-expanded=\""
                                   + expanded + "\" aria-label=\""
                                   + (expanded
                ? "Collapse"
                : "Expand") + "\">");
        appendExpander(builder, expanded);
        builder.appendEscaped(node.getName());
        builder.appendHtmlConstant("</button>");

        final String label = allExpanded
                ? "Collapse all"
                : "Expand all";
        builder.appendHtmlConstant("<button type=\"button\" tabindex=\"-1\" "
                                   + "class=\"wbm-icon-button wbm-root__action\" "
                                   + ATTR_ACTION + "=\"" + ACTION_EXPAND_ALL + "\" aria-label=\""
                                   + label + "\" title=\"" + label + "\">");
        MenuHtml.appendIcon(builder, "wbm-icon", allExpanded
                ? "wbm-icon-collapse-all"
                : "wbm-icon-expand-all");
        builder.appendHtmlConstant("</button></div>");
    }

    private void renderItem(final SafeHtmlBuilder builder, final StoryTreeNode node) {
        final boolean selected = node.getId().equals(model.getSelectedId());
        final boolean highlighted = node.getId().equals(highlightedId);
        final boolean expandable = node.isExpandable();
        final String classes = "wbm-item"
                               + (selected
                ? " wbm-item--selected"
                : "")
                               + (highlighted
                ? " wbm-item--highlighted"
                : "");

        builder.appendHtmlConstant("<div class=\"" + classes + "\" data-nodetype=\""
                                   + node.getType().getNodeType() + "\" " + ATTR_ITEM_ID + "=\"")
                .appendEscaped(node.getId())
                .appendHtmlConstant("\" id=\"").appendEscaped(itemElementId(node.getId()))
                .appendHtmlConstant("\" data-selected=\"" + selected + "\">");

        if (node.getType() == Type.STORY) {
            // Stories are links so they can be opened in a new tab
            builder.appendHtmlConstant("<a class=\"wbm-item__button\" tabindex=\"-1\" href=\"")
                    .appendEscaped(StoryUrls.managerUrl(node.getId()))
                    .appendHtmlConstant("\"");
        } else {
            builder.appendHtmlConstant("<div role=\"button\" class=\"wbm-item__button\" "
                                       + ATTR_ACTION + "=\"" + ACTION_TOGGLE + "\" aria-expanded=\""
                                       + model.isExpanded(node.getId()) + "\"");
        }
        builder.appendHtmlConstant(" style=\"padding-left: " + getIndent(node) + "px\">");

        builder.appendHtmlConstant("<span class=\"wbm-item__icons\">");
        if (expandable) {
            appendExpander(builder, model.isExpanded(node.getId()));
        }
        MenuHtml.appendIcon(builder, "wbm-item__icon", iconFor(node.getType()));
        builder.appendHtmlConstant("</span><span class=\"wbm-item__label\">")
                .appendEscaped(node.getName())
                .appendHtmlConstant("</span>");
        appendStatus(builder, node);
        builder.appendHtmlConstant(node.getType() == Type.STORY
                ? "</a>"
                : "</div>");

        if (node.getType() != Type.GROUP) {
            builder.appendHtmlConstant("<button type=\"button\" tabindex=\"-1\" "
                                       + "class=\"wbm-icon-button wbm-item__menu\" "
                                       + ATTR_ACTION + "=\"" + ACTION_CONTEXT_MENU + "\" "
                                       + "aria-label=\"Open context menu\">");
            MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-ellipsis");
            builder.appendHtmlConstant("</button>");
        }
        builder.appendHtmlConstant("</div>");
    }

    private void appendStatus(final SafeHtmlBuilder builder, final StoryTreeNode node) {
        final String status = statusProvider.apply(node);
        // The status is put into class names and an icon id, so only the known statuses are shown
        if (TagFilter.isStatusTag(status)) {
            builder.appendHtmlConstant("<span class=\"wbm-item__status wbm-item__status--" + status
                                       + "\" title=\"" + status + "\">");
            MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-" + status);
            builder.appendHtmlConstant("</span>");
        }
    }

    private static int getIndent(final StoryTreeNode node) {
        final int indent = BASE_INDENT_PX + (node.getDepth() * INDENT_PER_LEVEL_PX);
        return node.isExpandable()
                ? indent
                : indent + LEAF_EXTRA_INDENT_PX;
    }

    private SafeHtml renderSearchResults() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (results.isEmpty()) {
            builder.appendHtmlConstant("<div class=\"wbm-no-results\"><strong>No components found</strong>"
                                       + "<small>Find components by name or path.</small></div>");
            return builder.toSafeHtml();
        }

        builder.appendHtmlConstant("<div class=\"wbm-results\" role=\"listbox\">");
        for (final SearchResult result : results) {
            final StoryTreeNode node = result.getNode();
            final boolean highlighted = node.getId().equals(highlightedId);
            builder.appendHtmlConstant("<a class=\"wbm-result" + (highlighted
                            ? " wbm-result--highlighted"
                            : "") + "\" role=\"option\" tabindex=\"-1\" href=\"")
                    .appendEscaped(StoryUrls.managerUrl(getTargetStoryId(node)))
                    .appendHtmlConstant("\" data-nodetype=\"" + node.getType().getNodeType() + "\" "
                                        + ATTR_ITEM_ID + "=\"")
                    .appendEscaped(node.getId())
                    .appendHtmlConstant("\" id=\"").appendEscaped(itemElementId(node.getId()))
                    .appendHtmlConstant("\">");
            MenuHtml.appendIcon(builder, "wbm-item__icon", iconFor(node.getType()));
            builder.appendHtmlConstant("<span class=\"wbm-result__text\"><span class=\"wbm-result__name\">");
            appendHighlighted(builder, node.getName(), result);
            builder.appendHtmlConstant("</span><span class=\"wbm-result__path\">")
                    .appendEscaped(String.join("/ ", StorySearch.getAncestorNames(node)))
                    .appendHtmlConstant("</span></span></a>");
        }
        builder.appendHtmlConstant("</div>");
        return builder.toSafeHtml();
    }

    private static void appendHighlighted(final SafeHtmlBuilder builder,
                                          final String text,
                                          final SearchResult result) {
        boolean inMark = false;
        for (int i = 0; i < text.length(); i++) {
            final boolean matched = result.isMatched(i);
            if (matched && !inMark) {
                builder.appendHtmlConstant("<mark>");
                inMark = true;
            } else if (!matched && inMark) {
                builder.appendHtmlConstant("</mark>");
                inMark = false;
            }
            builder.appendEscaped(String.valueOf(text.charAt(i)));
        }
        if (inMark) {
            builder.appendHtmlConstant("</mark>");
        }
    }

    private static void appendExpander(final SafeHtmlBuilder builder, final boolean expanded) {
        builder.appendHtmlConstant("<span class=\"wbm-expander" + (expanded
                ? " wbm-expander--open"
                : "") + "\">");
        MenuHtml.appendIcon(builder, null, "wbm-icon-expander");
        builder.appendHtmlConstant("</span>");
    }

    private static String iconFor(final Type type) {
        switch (type) {
            case GROUP:
                return "wbm-icon-group";
            case COMPONENT:
                return "wbm-icon-component";
            default:
                return "wbm-icon-story";
        }
    }

    // ---------- Mouse ----------

    private void onTreeClick(final Event event) {
        final Element target = getTargetElement(event);
        final Element item = BrowserUtil.closest(target, "[" + ATTR_ITEM_ID + "]");
        if (item == null) {
            return;
        }
        final String itemId = item.getAttribute(ATTR_ITEM_ID);
        final StoryTreeNode node = model.getNode(itemId);
        if (node == null) {
            return;
        }

        final Element actionElement = BrowserUtil.closest(target, "[" + ATTR_ACTION + "]");
        final String action = actionElement != null
                ? actionElement.getAttribute(ATTR_ACTION)
                : null;

        if (ACTION_CONTEXT_MENU.equals(action)) {
            event.preventDefault();
            showContextMenu(actionElement, node);
        } else if (ACTION_EXPAND_ALL.equals(action)) {
            model.setAllExpanded(itemId, !model.isAllExpanded(itemId));
            render();
        } else if (node.getType() == Type.STORY || !query.isEmpty()) {
            // Let the browser handle modified clicks, e.g. ctrl+click to open in a new tab
            if (isModifiedClick(event)) {
                return;
            }
            event.preventDefault();
            selectNode(node);
        } else {
            highlightedId = itemId;
            activate(node);
        }
    }

    private void showContextMenu(final Element button, final StoryTreeNode node) {
        final List<MenuItem> items = new ArrayList<>();
        items.add(MenuItem.of(MENU_OPEN_IN_EDITOR, "Open in editor")
                .icon("wbm-icon-editor")
                .shortcut(shortcutHandler.getShortcuts().get(ShortcutAction.OPEN_IN_EDITOR)));
        if (node.getType() == Type.STORY) {
            items.add(MenuItem.of(MENU_COPY_NAME, "Copy story name").icon("wbm-icon-copy"));
        }
        final Element itemElement = BrowserUtil.closest(button, "[" + ATTR_ITEM_ID + "]");
        itemElement.addClassName("wbm-item--menu-open");
        final Popover popover = Popover.toggle(button, MenuHtml.render(MenuItem.groups(items)),
                Popover.Align.END, target -> {
                    final String menuItemId = MenuHtml.getClickedItemId(target);
                    if (menuItemId != null) {
                        Popover.hideCurrent();
                    }
                    if (MENU_OPEN_IN_EDITOR.equals(menuItemId)) {
                        listener.onOpenInEditor(node);
                    } else if (MENU_COPY_NAME.equals(menuItemId)) {
                        BrowserUtil.copyToClipboard(node.getName());
                    }
                });
        if (popover != null) {
            popover.setCloseHandler(() -> itemElement.removeClassName("wbm-item--menu-open"));
        } else {
            itemElement.removeClassName("wbm-item--menu-open");
        }
    }

    // ---------- Keyboard ----------

    private void onTreeKeyDown(final Event event) {
        if (!query.isEmpty() || event.getAltKey() || event.getCtrlKey() || event.getMetaKey()) {
            return;
        }
        final List<StoryTreeNode> visible = getHighlightableNodes();
        if (visible.isEmpty()) {
            return;
        }
        // The highlighted node may have been hidden by collapsing an ancestor, in which case
        // carry on from the nearest visible ancestor
        highlightedId = model.getNearestVisibleId(highlightedId);
        final StoryTreeNode current = model.getNode(highlightedId);
        final int index = current != null
                ? visible.indexOf(current)
                : -1;

        switch (event.getKeyCode()) {
            case KeyCodes.KEY_DOWN:
                highlight(visible.get(Math.min(index + 1, visible.size() - 1)));
                break;
            case KeyCodes.KEY_UP:
                highlight(visible.get(Math.max(index - 1, 0)));
                break;
            case KeyCodes.KEY_RIGHT:
                if (current != null && current.isExpandable()) {
                    if (!model.isExpanded(current.getId())) {
                        model.setExpanded(current.getId(), true);
                        render();
                    } else if (!current.getChildren().isEmpty()) {
                        highlight(current.getChildren().get(0));
                    }
                }
                break;
            case KeyCodes.KEY_LEFT:
                if (current != null) {
                    if (current.isExpandable() && model.isExpanded(current.getId())) {
                        model.setExpanded(current.getId(), false);
                        render();
                    } else if (current.getParent() != null
                               && current.getParent().getType() != Type.ROOT) {
                        highlight(current.getParent());
                    }
                }
                break;
            case KeyCodes.KEY_ENTER:
            case KeyCodes.KEY_SPACE:
                if (current != null) {
                    activate(current);
                }
                break;
            default:
                return;
        }
        event.preventDefault();
    }

    private List<StoryTreeNode> getHighlightableNodes() {
        final List<StoryTreeNode> nodes = new ArrayList<>();
        for (final StoryTreeNode node : model.getVisibleNodes()) {
            if (node.getType() != Type.ROOT) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private void highlight(final StoryTreeNode node) {
        highlightedId = node.getId();
        render();
    }

    /// What clicking or pressing enter on a node does.
    private void activate(final StoryTreeNode node) {
        if (node.getType() == Type.STORY) {
            listener.onStorySelected(node.getId());
        } else if (node.getType() == Type.COMPONENT && !model.isExpanded(node.getId())) {
            // Expanding a component also shows its first story, as in Storybook
            model.setExpanded(node.getId(), true);
            listener.onStorySelected(getTargetStoryId(node));
        } else {
            model.toggleExpanded(node.getId());
            render();
        }
    }

    private void selectNode(final StoryTreeNode node) {
        final String storyId = getTargetStoryId(node);
        if (!query.isEmpty()) {
            clearSearch();
            searchInput.blur();
        }
        highlightedId = null;
        listener.onStorySelected(storyId);
    }

    /// @return The story to show when a node is chosen, i.e. the node itself for a story, or
    /// the first story below the node otherwise.
    private static String getTargetStoryId(final StoryTreeNode node) {
        StoryTreeNode current = node;
        while (current.getType() != Type.STORY && !current.getChildren().isEmpty()) {
            current = current.getChildren().get(0);
        }
        return current.getId();
    }

    private void onSearchKeyDown(final Event event) {
        final int keyCode = event.getKeyCode();
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            clearSearch();
            searchInput.blur();
        } else if (!results.isEmpty()
                   && (keyCode == KeyCodes.KEY_DOWN || keyCode == KeyCodes.KEY_UP)) {
            event.preventDefault();
            int index = indexOfHighlightedResult();
            index = keyCode == KeyCodes.KEY_DOWN
                    ? Math.min(index + 1, results.size() - 1)
                    : Math.max(index - 1, 0);
            highlightedId = results.get(index).getNode().getId();
            render();
        } else if (keyCode == KeyCodes.KEY_ENTER && !results.isEmpty()) {
            event.preventDefault();
            final int index = Math.max(indexOfHighlightedResult(), 0);
            selectNode(results.get(index).getNode());
        }
    }

    private int indexOfHighlightedResult() {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).getNode().getId().equals(highlightedId)) {
                return i;
            }
        }
        return -1;
    }

    private void setQuery(final String value) {
        query = value == null
                ? ""
                : value.trim();
        results = StorySearch.search(topLevelNodes, query);
        highlightedId = null;
        if (query.isEmpty()) {
            searchField.removeClassName("wbm-search__field--has-text");
        } else {
            searchField.addClassName("wbm-search__field--has-text");
        }
        render();
        if (query.isEmpty()) {
            scrollToSelected();
        }
    }

    private void clearSearch() {
        searchInput.setValue("");
        setQuery("");
    }

    private void setSearchFocused(final boolean focused) {
        if (focused) {
            searchField.addClassName("wbm-search__field--focused");
            searchInput.setAttribute("placeholder", FOCUSED_PLACEHOLDER);
        } else {
            searchField.removeClassName("wbm-search__field--focused");
            searchInput.setAttribute("placeholder", PLACEHOLDER);
        }
    }

    private static Element getTargetElement(final NativeEvent event) {
        final EventTarget eventTarget = event.getEventTarget();
        if (eventTarget == null || !Element.is(eventTarget)) {
            return null;
        }
        return Element.as(eventTarget);
    }

    private static boolean isModifiedClick(final NativeEvent event) {
        return event.getCtrlKey()
               || event.getMetaKey()
               || event.getShiftKey()
               || event.getButton() != NativeEvent.BUTTON_LEFT;
    }


    // --------------------------------------------------------------------------------


    /// Receives the user's choices in the sidebar.
    public interface Listener {

        /// @param storyId The id of the story the user selected.
        void onStorySelected(String storyId);

        /// @param node The story or component whose source the user wants to open.
        void onOpenInEditor(StoryTreeNode node);
    }
}
