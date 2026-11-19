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
import stroom.gwt.workbench.framework.client.play.PlayRunner.Control;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/// The Interactions addon, which shows the steps of the story's play function as they run, with
/// debugger controls to step through them, as in React Storybook.
public class InteractionsAddon extends Addon {

    private static final String ATTR_CONTROL = "data-control";
    private static final String ATTR_TOGGLE = "data-toggle-row";
    private static final String ATTR_OPEN_SOURCE = "data-open-source";
    private static final String ATTR_SCROLL = "data-scroll-to-end";

    private final Consumer<Control> controlHandler;
    private final Runnable openSourceHandler;
    private final Set<Integer> collapsedRows = new HashSet<>();
    private String sourceFileName;
    private JSONObject state;

    /// @param controlHandler    Called when the user clicks a debugger control.
    /// @param openSourceHandler Called when the user clicks the source file name.
    public InteractionsAddon(final Consumer<Control> controlHandler, final Runnable openSourceHandler) {
        this.controlHandler = controlHandler;
        this.openSourceHandler = openSourceHandler;
    }

    @Override
    public String getTitle() {
        return "Interactions";
    }

    @Override
    public String getBadge() {
        final JSONArray entries = getEntries();
        return entries != null && entries.size() > 0
                ? String.valueOf(entries.size())
                : null;
    }

    @Override
    protected void onAttach() {
        BrowserUtil.addListener(getElement(), "click", event -> onClick(Element.as(event.getEventTarget())));
        render();
    }

    /// Clears the steps, e.g. when a different story is selected.
    ///
    /// @param sourceFileName The name of the story's source file to show, or null.
    public void reset(final String sourceFileName) {
        this.sourceFileName = sourceFileName;
        this.state = null;
        collapsedRows.clear();
        render();
        badgeChanged();
    }

    /// Shows the progress of the play function, as reported by the preview.
    ///
    /// @param json The progress, as JSON.
    public void update(final String json) {
        try {
            final JSONValue value = JSONParser.parseStrict(json);
            state = value.isObject();
        } catch (final RuntimeException e) {
            state = null;
        }
        render();
        badgeChanged();
    }

    private JSONArray getEntries() {
        if (state == null || state.get("entries") == null) {
            return null;
        }
        final JSONValue entries = state.get("entries");
        return entries.isArray();
    }

    private static JSONObject getEntry(final JSONArray entries, final int index) {
        if (index >= entries.size()) {
            return null;
        }
        final JSONValue value = entries.get(index);
        return value != null
                ? value.isObject()
                : null;
    }

    private String getString(final JSONObject object, final String key) {
        final JSONValue value = object.get(key);
        return value != null && value.isString() != null
                ? value.isString().stringValue()
                : null;
    }

    private int getInt(final JSONObject object, final String key) {
        final JSONValue value = object.get(key);
        return value != null && value.isNumber() != null
                ? (int) value.isNumber().doubleValue()
                : 0;
    }

    // ---------- Rendering ----------

    private void render() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        final String status = state != null
                ? getString(state, "status")
                : null;
        final int nextStep = state != null
                ? getInt(state, "nextStep")
                : 0;
        final int stepCount = state != null
                ? getInt(state, "stepCount")
                : 0;
        final boolean busy = "PLAYING".equals(status);
        final boolean failed = "ERRORED".equals(status);

        builder.appendHtmlConstant("<div class=\"wbm-interactions\"><div class=\"wbm-subbar\">");
        appendStatusBadge(builder, status);
        builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-secondary-button wbm-secondary-button--small\" "
                                   + ATTR_SCROLL + ">Scroll to end</button><span class=\"wbm-subbar__divider\"></span>"
                                   + "<span class=\"wbm-subbar__group\" role=\"group\" "
                                   + "aria-label=\"Component test playback controls\">");
        // After a failure the user can go back to before the step that failed
        final boolean atStart = nextStep == 0 && !failed;
        appendControl(builder, Control.REWIND, "Go to start", "wbm-icon-rewind", busy || atStart);
        appendControl(builder, Control.BACK, "Go back", "wbm-icon-play-back", busy || atStart);
        appendControl(builder, Control.NEXT, "Go forward", "wbm-icon-play-next",
                busy || failed || nextStep >= stepCount);
        appendControl(builder, Control.END, "Go to end", "wbm-icon-fast-forward",
                busy || failed || nextStep >= stepCount);
        appendControl(builder, Control.RERUN, "Rerun", "wbm-icon-sync", busy);
        builder.appendHtmlConstant("</span>");
        if (sourceFileName != null) {
            builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-subbar__source\" " + ATTR_OPEN_SOURCE
                                       + " aria-label=\"Open in editor\">")
                    .appendEscaped(sourceFileName)
                    .appendHtmlConstant("</button>");
        }
        builder.appendHtmlConstant("</div>");

        final JSONArray entries = getEntries();
        if (entries == null || entries.size() == 0) {
            appendEmptyState(builder, "No play function",
                    "Give the story a play function with withPlay(...) to click, type and check the widget "
                    + "automatically. Each step is listed here as it runs.", null);
        } else {
            builder.appendHtmlConstant("<ol class=\"wbm-interaction-list\">");
            int hiddenBelowDepth = Integer.MAX_VALUE;
            for (int i = 0; i < entries.size(); i++) {
                final JSONObject entry = getEntry(entries, i);
                if (entry == null) {
                    continue;
                }
                final int depth = getInt(entry, "depth");
                if (depth > hiddenBelowDepth) {
                    continue;
                }
                hiddenBelowDepth = Integer.MAX_VALUE;
                final JSONObject nextEntry = getEntry(entries, i + 1);
                final boolean hasChildren = nextEntry != null && getInt(nextEntry, "depth") > depth;
                final boolean collapsed = collapsedRows.contains(i);
                if (hasChildren && collapsed) {
                    hiddenBelowDepth = depth;
                }
                appendRow(builder, i, entry, depth, hasChildren, collapsed);
            }
            builder.appendHtmlConstant("</ol>");
        }
        builder.appendHtmlConstant("</div>");
        getElement().setInnerSafeHtml(builder.toSafeHtml());
    }

    private static void appendStatusBadge(final SafeHtmlBuilder builder, final String status) {
        final String label;
        final String modifier;
        if ("ERRORED".equals(status)) {
            label = "Fail";
            modifier = "fail";
        } else if ("PLAYING".equals(status) || "PAUSED".equals(status)) {
            label = "Runs";
            modifier = "runs";
        } else {
            label = "Pass";
            modifier = "pass";
        }
        builder.appendHtmlConstant("<span class=\"wbm-status wbm-status--" + modifier + "\" "
                                   + "aria-label=\"Story status: " + label + "\">")
                .appendEscaped(label.toUpperCase())
                .appendHtmlConstant("</span>");
    }

    private static void appendControl(final SafeHtmlBuilder builder,
                                      final Control control,
                                      final String label,
                                      final String icon,
                                      final boolean disabled) {
        builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-icon-button\" " + ATTR_CONTROL + "=\""
                                   + control.name() + "\" aria-label=\"" + label + "\" title=\"" + label + "\""
                                   + (disabled
                ? " disabled"
                : "") + ">");
        MenuHtml.appendIcon(builder, "wbm-icon", icon);
        builder.appendHtmlConstant("</button>");
    }

    private void appendRow(final SafeHtmlBuilder builder,
                           final int index,
                           final JSONObject entry,
                           final int depth,
                           final boolean hasChildren,
                           final boolean collapsed) {
        // The status comes from a message so is mapped to one of a fixed set of class modifiers
        final String status = HtmlTokens.interactionStepStatus(getString(entry, "status"));
        final String text = getString(entry, "text");
        final String error = getString(entry, "error");
        builder.appendHtmlConstant("<li class=\"wbm-interaction wbm-interaction--" + status
                                   + "\"><div class=\"wbm-interaction__row\" style=\"padding-left: "
                                   + (15 + Math.max(0, depth) * 20) + "px\">");
        builder.appendHtmlConstant("<span class=\"wbm-interaction__icon\">");
        switch (status) {
            case HtmlTokens.STEP_DONE:
                MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-check");
                break;
            case HtmlTokens.STEP_ERROR:
                MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-cross");
                break;
            case HtmlTokens.STEP_ACTIVE:
                builder.appendHtmlConstant("<span class=\"wbm-spinner\"></span>");
                break;
            default:
                MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-circle-hollow");
                break;
        }
        builder.appendHtmlConstant("</span><code class=\"wbm-interaction__code\">");
        CodeHighlighter.append(builder, text != null
                ? text
                : "");
        builder.appendHtmlConstant("</code>");
        if (hasChildren) {
            builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-icon-button\" " + ATTR_TOGGLE + "=\""
                                       + index + "\" aria-label=\"" + (collapsed
                    ? "Expand"
                    : "Collapse") + " nested interaction steps\">");
            MenuHtml.appendIcon(builder, "wbm-icon", collapsed
                    ? "wbm-icon-chevron-down"
                    : "wbm-icon-chevron-up");
            builder.appendHtmlConstant("</button>");
        }
        builder.appendHtmlConstant("</div>");
        if (error != null) {
            builder.appendHtmlConstant("<pre class=\"wbm-interaction__error\">")
                    .appendEscaped(error)
                    .appendHtmlConstant("</pre>");
        }
        builder.appendHtmlConstant("</li>");
    }

    // ---------- Events ----------

    private void onClick(final Element target) {
        final Element control = BrowserUtil.closest(target, "[" + ATTR_CONTROL + "]");
        if (control != null && !control.hasAttribute("disabled")) {
            controlHandler.accept(Control.valueOf(control.getAttribute(ATTR_CONTROL)));
            return;
        }
        final Element toggle = BrowserUtil.closest(target, "[" + ATTR_TOGGLE + "]");
        if (toggle != null) {
            final int index = Integer.parseInt(toggle.getAttribute(ATTR_TOGGLE));
            if (!collapsedRows.remove(index)) {
                collapsedRows.add(index);
            }
            render();
            return;
        }
        if (BrowserUtil.closest(target, "[" + ATTR_OPEN_SOURCE + "]") != null) {
            openSourceHandler.run();
            return;
        }
        if (BrowserUtil.closest(target, "[" + ATTR_SCROLL + "]") != null) {
            final NodeList<Element> rows = getElement().getElementsByTagName("li");
            if (rows.getLength() > 0) {
                BrowserUtil.scrollIntoViewIfNeeded(rows.getItem(rows.getLength() - 1));
            }
        }
    }
}
