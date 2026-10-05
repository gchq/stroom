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

package stroom.gwt.workbench.framework.client.manager.addons;

import stroom.gwt.workbench.framework.client.BrowserUtil;
import stroom.gwt.workbench.framework.client.manager.HtmlTokens;
import stroom.gwt.workbench.framework.client.manager.MenuHtml;

import com.google.gwt.dom.client.Element;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.UriUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/// The Accessibility addon, which shows the results of checking the story with axe-core, as in
/// React Storybook: violations, passes and inconclusive results, each rule expandable to show the
/// elements involved.
public class AccessibilityAddon extends Addon {

    private static final String ATTR_RESULT_TAB = "data-result-tab";
    private static final String ATTR_RULE = "data-rule";
    private static final String ATTR_NODE = "data-node";
    private static final String ATTR_ACTION = "data-action";
    private static final String ACTION_HIGHLIGHT = "highlight";
    private static final String ACTION_COLLAPSE_ALL = "collapse-all";
    private static final String ACTION_RERUN = "rerun";
    private static final String ACTION_JUMP = "jump";
    private static final String ACTION_COPY_LINK = "copy-link";

    private final Listener listener;
    private final Set<String> expandedRules = new HashSet<>();
    private final Map<String, Integer> selectedNodes = new HashMap<>();
    private ResultType resultType = ResultType.VIOLATIONS;
    private String status;
    private JSONObject results;
    private String error;
    private boolean highlight;
    // A result to expand once the results arrive, e.g. from a copied link
    private String pendingSelection;

    /// @param listener Told when the user wants to rerun the checks or highlight elements.
    public AccessibilityAddon(final Listener listener) {
        this.listener = listener;
    }

    @Override
    public String getTitle() {
        return "Accessibility";
    }

    @Override
    public String getBadge() {
        final int violations = count(ResultType.VIOLATIONS);
        return violations > 0
                ? String.valueOf(violations)
                : null;
    }

    @Override
    protected void onAttach() {
        BrowserUtil.addListener(getElement(), "click", event -> onClick(Element.as(event.getEventTarget())));
        render();
    }

    /// Clears the results, e.g. when a different story is selected.
    public void reset() {
        status = null;
        pendingSelection = null;
        results = null;
        error = null;
        expandedRules.clear();
        selectedNodes.clear();
        render();
        badgeChanged();
    }

    /// Selects and expands a result once the results are available, e.g. when the page was opened
    /// from a link copied with 'Copy link'.
    ///
    /// @param resultKey The result, e.g. `VIOLATIONS.color-contrast`, or null for none.
    public void selectResult(final String resultKey) {
        pendingSelection = resultKey;
        applyPendingSelection();
        render();
    }

    private void applyPendingSelection() {
        if (pendingSelection == null || results == null) {
            return;
        }
        final int dot = pendingSelection.indexOf('.');
        final ResultType type = dot > 0
                ? ResultType.fromName(pendingSelection.substring(0, dot))
                : null;
        if (type != null) {
            resultType = type;
            expandedRules.add(pendingSelection);
        }
        pendingSelection = null;
    }

    /// Shows a result posted by the preview.
    ///
    /// @param newStatus `running`, `ready`, `error` or `unavailable`.
    /// @param detail    The results as JSON when ready, or the error message.
    public void update(final String newStatus, final String detail) {
        status = newStatus;
        error = null;
        if ("ready".equals(newStatus)) {
            try {
                results = detail != null
                        ? JSONParser.parseStrict(detail).isObject()
                        : null;
            } catch (final RuntimeException e) {
                results = null;
            }
            applyPendingSelection();
            sendHighlight();
        } else if ("error".equals(newStatus)) {
            error = detail;
        }
        render();
        badgeChanged();
    }

    private JSONArray getRules(final ResultType type) {
        return getArray(results, type.key);
    }

    /// @return The array in an object's property, or an empty array if it isn't an array.
    private static JSONArray getArray(final JSONObject object, final String key) {
        final JSONValue value = object != null
                ? object.get(key)
                : null;
        final JSONArray array = value != null
                ? value.isArray()
                : null;
        return array != null
                ? array
                : new JSONArray();
    }

    /// @return The object at an index of an array, or null if it isn't an object.
    private static JSONObject getObject(final JSONArray array, final int index) {
        if (index < 0 || index >= array.size()) {
            return null;
        }
        final JSONValue value = array.get(index);
        return value != null
                ? value.isObject()
                : null;
    }

    private int count(final ResultType type) {
        return getRules(type).size();
    }

    private static String getString(final JSONObject object, final String key) {
        if (object == null) {
            return null;
        }
        final JSONValue value = object.get(key);
        return value != null && value.isString() != null
                ? value.isString().stringValue()
                : null;
    }

    // ---------- Rendering ----------

    private void render() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"wbm-a11y\">");
        if ("unavailable".equals(status)) {
            appendEmptyState(builder, "Accessibility checks are not available",
                    "axe-core could not be loaded. Fetch it with "
                    + "'./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchFetchAxe' (which needs npm) "
                    + "then reload the workbench.", null);
        } else if ("error".equals(status)) {
            appendEmptyState(builder, "The accessibility scan encountered an error",
                    error != null
                            ? error
                            : "Unknown error", null);
        } else if (results == null) {
            appendEmptyState(builder, "Accessibility scan in progress",
                    "Checking the story with axe-core...", null);
        } else {
            appendTabs(builder);
            appendRules(builder);
        }
        builder.appendHtmlConstant("</div>");
        getElement().setInnerSafeHtml(builder.toSafeHtml());
    }

    private void appendTabs(final SafeHtmlBuilder builder) {
        builder.appendHtmlConstant("<div class=\"wbm-subbar wbm-subbar--tabs\"><span class=\"wbm-subbar__tabs\" "
                                   + "role=\"tablist\">");
        for (final ResultType type : ResultType.values()) {
            final boolean selected = type == resultType;
            builder.appendHtmlConstant("<button type=\"button\" role=\"tab\" class=\"wbm-tab" + (selected
                            ? " wbm-tab--selected"
                            : "") + "\" aria-selected=\"" + selected + "\" " + ATTR_RESULT_TAB + "=\""
                                       + type.name() + "\">")
                    .appendEscaped(type.label)
                    .appendHtmlConstant("<span class=\"wbm-badge" + (type == ResultType.VIOLATIONS && selected
                            ? ""
                            : " wbm-badge--neutral") + "\">")
                    .append(count(type))
                    .appendHtmlConstant("</span></button>");
        }
        builder.appendHtmlConstant("</span><span class=\"wbm-subbar__group\">");
        appendAction(builder, ACTION_HIGHLIGHT, highlight
                ? "Hide highlights"
                : "Highlight elements with accessibility test results", highlight
                ? "wbm-icon-eye-close"
                : "wbm-icon-eye", highlight);
        appendAction(builder, ACTION_COLLAPSE_ALL, expandedRules.isEmpty()
                ? "Expand all results"
                : "Collapse all results", expandedRules.isEmpty()
                ? "wbm-icon-expand-alt"
                : "wbm-icon-collapse", !expandedRules.isEmpty());
        appendAction(builder, ACTION_RERUN, "Rerun accessibility scan", "wbm-icon-sync", false);
        builder.appendHtmlConstant("</span></div>");
    }

    private static void appendAction(final SafeHtmlBuilder builder,
                                     final String action,
                                     final String label,
                                     final String icon,
                                     final boolean active) {
        builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-icon-button" + (active
                ? " wbm-icon-button--active"
                : "") + "\" " + ATTR_ACTION + "=\"" + action + "\" aria-label=\"" + label + "\" title=\""
                                   + label + "\">");
        MenuHtml.appendIcon(builder, "wbm-icon", icon);
        builder.appendHtmlConstant("</button>");
    }

    private void appendRules(final SafeHtmlBuilder builder) {
        final JSONArray rules = getRules(resultType);
        if (rules.size() == 0) {
            builder.appendHtmlConstant("<div class=\"wbm-a11y__none\">")
                    .appendEscaped(resultType.emptyText)
                    .appendHtmlConstant("</div>");
            return;
        }
        builder.appendHtmlConstant("<ul class=\"wbm-a11y__rules\">");
        for (int i = 0; i < rules.size(); i++) {
            final JSONObject rule = getObject(rules, i);
            if (rule != null) {
                appendRule(builder, rule);
            }
        }
        builder.appendHtmlConstant("</ul>");
    }

    private void appendRule(final SafeHtmlBuilder builder, final JSONObject rule) {
        final String id = getString(rule, "id");
        final String key = ruleKey(id);
        final boolean expanded = expandedRules.contains(key);
        final JSONArray nodes = getArray(rule, "nodes");
        // The impact is put into a class name so only axe-core's impacts are allowed
        final String impact = HtmlTokens.accessibilityImpact(getString(rule, "impact"));

        builder.appendHtmlConstant("<li class=\"wbm-a11y__rule\"><button type=\"button\" "
                                   + "class=\"wbm-a11y__rule-header\" " + ATTR_RULE + "=\"")
                .appendEscaped(key)
                .appendHtmlConstant("\" aria-expanded=\"" + expanded + "\"><span class=\"wbm-a11y__rule-title\">"
                                    + "<strong>")
                .appendEscaped(nullToEmpty(getString(rule, "help")))
                .appendHtmlConstant("</strong> <code>")
                .appendEscaped(nullToEmpty(id))
                .appendHtmlConstant("</code></span>");
        if (impact != null && resultType != ResultType.PASSES) {
            builder.appendHtmlConstant("<span class=\"wbm-impact wbm-impact--" + impact + "\">")
                    .appendEscaped(capitalise(impact))
                    .appendHtmlConstant("</span>");
        }
        builder.appendHtmlConstant("<span class=\"wbm-a11y__count\">")
                .append(nodes.size())
                .appendHtmlConstant("</span>");
        MenuHtml.appendIcon(builder, "wbm-icon", expanded
                ? "wbm-icon-chevron-up"
                : "wbm-icon-chevron-down");
        builder.appendHtmlConstant("</button>");

        if (expanded) {
            builder.appendHtmlConstant("<div class=\"wbm-a11y__details\"><p>")
                    .appendEscaped(nullToEmpty(getString(rule, "description")))
                    .appendHtmlConstant(" <a target=\"_blank\" rel=\"noopener noreferrer\" href=\"")
                    .appendEscaped(UriUtils.sanitizeUri(nullToEmpty(getString(rule, "helpUrl"))))
                    .appendHtmlConstant("\">Learn how to resolve this violation");
            MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-chevron-small-right");
            builder.appendHtmlConstant("</a></p>");
            appendNodes(builder, key, nodes);
            builder.appendHtmlConstant("</div>");
        }
        builder.appendHtmlConstant("</li>");
    }

    private void appendNodes(final SafeHtmlBuilder builder, final String key, final JSONArray nodes) {
        if (nodes.size() == 0) {
            return;
        }
        final int selected = Math.max(0, Math.min(selectedNodes.getOrDefault(key, 0), nodes.size() - 1));
        builder.appendHtmlConstant("<div class=\"wbm-a11y__nodes\"><ol class=\"wbm-a11y__node-list\">");
        for (int i = 0; i < nodes.size(); i++) {
            builder.appendHtmlConstant("<li><button type=\"button\" class=\"wbm-a11y__node" + (i == selected
                            ? " wbm-a11y__node--selected"
                            : "") + "\" " + ATTR_RULE + "=\"")
                    .appendEscaped(key)
                    .appendHtmlConstant("\" " + ATTR_NODE + "=\"" + i + "\">")
                    .appendEscaped((i + 1) + ". " + nullToEmpty(getString(getObject(nodes, i), "html")))
                    .appendHtmlConstant("</button></li>");
        }
        builder.appendHtmlConstant("</ol>");

        final JSONObject node = getObject(nodes, selected);
        final String summary = getString(node, "failureSummary");
        builder.appendHtmlConstant("<div class=\"wbm-a11y__node-detail\">");
        if (summary != null) {
            builder.appendHtmlConstant("<p>").appendEscaped(summary).appendHtmlConstant("</p>");
        }
        builder.appendHtmlConstant("<div class=\"wbm-a11y__node-actions\">"
                                   + "<button type=\"button\" class=\"wbm-secondary-button\" " + ATTR_ACTION + "=\""
                                   + ACTION_JUMP + "\" " + ATTR_RULE + "=\"")
                .appendEscaped(key)
                .appendHtmlConstant("\">");
        MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-location");
        builder.appendHtmlConstant("Jump to element</button><button type=\"button\" class=\"wbm-secondary-button\" "
                                   + ATTR_ACTION + "=\"" + ACTION_COPY_LINK + "\" " + ATTR_RULE + "=\"")
                .appendEscaped(key)
                .appendHtmlConstant("\">");
        MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-copy");
        builder.appendHtmlConstant("Copy link</button></div><pre class=\"wbm-a11y__code\">"
                                   + "<span class=\"wbm-a11y__comment\">/* element */</span>\n")
                .appendEscaped(nullToEmpty(getString(node, "html")))
                .appendHtmlConstant("\n\n<span class=\"wbm-a11y__comment\">/* selector */</span>\n")
                .appendEscaped(getSelector(node))
                .appendHtmlConstant("</pre></div></div>");
    }

    /// @return The CSS selector of an element axe-core reported. Elements in shadow DOM have a
    /// nested array of selectors (host then element), which are joined with `>>>` as axe-core
    /// does.
    private static String getSelector(final JSONObject node) {
        final JSONArray target = getArray(node, "target");
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < target.size(); i++) {
            final String part = selectorPart(target.get(i));
            if (part != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private static String selectorPart(final JSONValue value) {
        if (value == null) {
            return null;
        }
        if (value.isString() != null) {
            return value.isString().stringValue();
        }
        final JSONArray nested = value.isArray();
        if (nested == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nested.size(); i++) {
            final String part = selectorPart(nested.get(i));
            if (part != null) {
                if (sb.length() > 0) {
                    sb.append(" >>> ");
                }
                sb.append(part);
            }
        }
        return sb.length() > 0
                ? sb.toString()
                : null;
    }

    private static String nullToEmpty(final String text) {
        return text != null
                ? text
                : "";
    }

    private static String capitalise(final String text) {
        return text.isEmpty()
                ? text
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String ruleKey(final String ruleId) {
        return resultType.name() + "." + ruleId;
    }

    // ---------- Events ----------

    private void onClick(final Element target) {
        final Element tab = BrowserUtil.closest(target, "[" + ATTR_RESULT_TAB + "]");
        if (tab != null) {
            final ResultType type = ResultType.fromName(tab.getAttribute(ATTR_RESULT_TAB));
            if (type != null) {
                resultType = type;
            }
            sendHighlight();
            render();
            return;
        }
        final Element actionElement = BrowserUtil.closest(target, "[" + ATTR_ACTION + "]");
        if (actionElement != null) {
            onAction(actionElement.getAttribute(ATTR_ACTION), actionElement.getAttribute(ATTR_RULE));
            return;
        }
        final Element ruleElement = BrowserUtil.closest(target, "[" + ATTR_RULE + "]");
        if (ruleElement != null) {
            final String key = ruleElement.getAttribute(ATTR_RULE);
            if (ruleElement.hasAttribute(ATTR_NODE)) {
                selectedNodes.put(key, Integer.parseInt(ruleElement.getAttribute(ATTR_NODE)));
            } else if (!expandedRules.remove(key)) {
                expandedRules.add(key);
            }
            render();
        }
    }

    private void onAction(final String action, final String key) {
        switch (action) {
            case ACTION_HIGHLIGHT:
                highlight = !highlight;
                sendHighlight();
                render();
                break;
            case ACTION_COLLAPSE_ALL:
                if (expandedRules.isEmpty()) {
                    final JSONArray rules = getRules(resultType);
                    for (int i = 0; i < rules.size(); i++) {
                        final JSONObject rule = getObject(rules, i);
                        if (rule != null) {
                            expandedRules.add(ruleKey(getString(rule, "id")));
                        }
                    }
                } else {
                    expandedRules.clear();
                }
                render();
                break;
            case ACTION_RERUN:
                results = null;
                render();
                listener.onRerun();
                break;
            case ACTION_JUMP:
                final JSONObject node = getSelectedNode(key);
                if (node != null) {
                    listener.onJumpTo(getSelector(node), resultType.colour);
                }
                break;
            case ACTION_COPY_LINK:
                listener.onCopyLink(key);
                break;
            default:
                break;
        }
    }

    private JSONObject getSelectedNode(final String key) {
        final JSONArray rules = getRules(resultType);
        for (int i = 0; i < rules.size(); i++) {
            final JSONObject rule = getObject(rules, i);
            if (rule != null && ruleKey(getString(rule, "id")).equals(key)) {
                final JSONArray nodes = getArray(rule, "nodes");
                final int selected = Math.min(selectedNodes.getOrDefault(key, 0), nodes.size() - 1);
                return getObject(nodes, selected);
            }
        }
        return null;
    }

    private void sendHighlight() {
        if (!highlight || results == null) {
            listener.onHighlight(null);
            return;
        }
        final JSONArray items = new JSONArray();
        final JSONArray rules = getRules(resultType);
        for (int i = 0; i < rules.size(); i++) {
            final JSONArray nodes = getArray(getObject(rules, i), "nodes");
            for (int j = 0; j < nodes.size(); j++) {
                final JSONObject node = getObject(nodes, j);
                if (node == null) {
                    continue;
                }
                final JSONObject item = new JSONObject();
                item.put("selector", new JSONString(getSelector(node)));
                item.put("colour", new JSONString(resultType.colour));
                items.set(items.size(), item);
            }
        }
        listener.onHighlight(items.toString());
    }


    // --------------------------------------------------------------------------------


    private enum ResultType {
        VIOLATIONS("violations", "Violations", "No accessibility violations found.", "#ff4400"),
        PASSES("passes", "Passes", "No accessibility checks passed.", "#66bf3c"),
        INCOMPLETE("incomplete", "Inconclusive", "No inconclusive accessibility checks.", "#ffae00");

        private final String key;
        private final String label;
        private final String emptyText;
        private final String colour;

        ResultType(final String key, final String label, final String emptyText, final String colour) {
            this.key = key;
            this.label = label;
            this.emptyText = emptyText;
            this.colour = colour;
        }

        private static ResultType fromName(final String name) {
            for (final ResultType type : values()) {
                if (type.name().equals(name)) {
                    return type;
                }
            }
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// Told about the user's actions in the addon.
    public interface Listener {

        /// Run the checks again.
        void onRerun();

        /// @param itemsJson A JSON array of `{selector, colour}` to outline, or null to remove them.
        void onHighlight(String itemsJson);

        /// @param selector The element to scroll to and outline.
        /// @param colour   The colour of the outline.
        void onJumpTo(String selector, String colour);

        /// @param resultKey The result, e.g. `VIOLATIONS.color-contrast`, to link to.
        void onCopyLink(String resultKey);
    }
}
