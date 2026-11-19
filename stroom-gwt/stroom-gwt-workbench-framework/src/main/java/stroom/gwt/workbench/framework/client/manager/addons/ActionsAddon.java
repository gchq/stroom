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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.LIElement;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.List;

/// The Actions addon, which logs the actions stories report, e.g. clicks, as in React
/// Storybook. Repeats of the same action are shown once with a count and only the most recent
/// actions are kept.
public class ActionsAddon extends Addon {

    private static final String ACTION_CLEAR = "clear-actions";
    private static final String LIST_SELECTOR = "ol.wbm-actions";

    private final ActionLog log = new ActionLog();

    @Override
    public String getTitle() {
        return "Actions";
    }

    @Override
    public String getBadge() {
        final int total = log.getTotal();
        return total > 0
                ? String.valueOf(total)
                : null;
    }

    @Override
    protected void onAttach() {
        BrowserUtil.addListener(getElement(), "click", event -> {
            if (BrowserUtil.closest(Element.as(event.getEventTarget()),
                    "[data-action=" + ACTION_CLEAR + "]") != null) {
                clear();
            }
        });
        render();
    }

    /// Logs an action.
    ///
    /// @param name   The name of the action, e.g. `onClick`, may be null.
    /// @param detail Detail about the action, may be null.
    public void log(final String name, final String detail) {
        final ActionLog.Change change = log.add(name, detail);
        // Only the changed rows are updated, rather than the whole log
        final Element list = BrowserUtil.querySelector(getElement(), LIST_SELECTOR);
        if (list == null) {
            render();
        } else {
            final List<ActionLog.Entry> entries = log.getEntries();
            final ActionLog.Entry last = entries.get(entries.size() - 1);
            if (change == ActionLog.Change.REPEATED && list.getLastChild() != null) {
                list.getLastChild().<Element>cast().setInnerSafeHtml(renderRow(last));
            } else {
                final LIElement row = Document.get().createLIElement();
                row.setInnerSafeHtml(renderRow(last));
                list.appendChild(row);
                if (change == ActionLog.Change.ADDED_AND_DROPPED_OLDEST && list.getFirstChild() != null) {
                    list.getFirstChild().removeFromParent();
                }
            }
        }
        badgeChanged();
    }

    /// Removes all logged actions, e.g. when a different story is selected.
    public void clear() {
        log.clear();
        render();
        badgeChanged();
    }

    private void render() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"wbm-actions-log\"><ol class=\"wbm-actions\">");
        for (final ActionLog.Entry entry : log.getEntries()) {
            builder.appendHtmlConstant("<li>")
                    .append(renderRow(entry))
                    .appendHtmlConstant("</li>");
        }
        builder.appendHtmlConstant("</ol><div class=\"wbm-actions__clear\">"
                                   + "<button type=\"button\" data-action=\"" + ACTION_CLEAR + "\">Clear</button>"
                                   + "</div></div>");
        getElement().setInnerSafeHtml(builder.toSafeHtml());
    }

    /// @return The content of an action's row.
    private static SafeHtml renderRow(final ActionLog.Entry entry) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<span class=\"wbm-actions__name\">")
                .appendEscaped(entry.getName())
                .appendHtmlConstant(":</span>");
        if (entry.getDetail() != null) {
            builder.appendHtmlConstant("<span class=\"wbm-actions__value\">")
                    .appendEscaped("\"" + entry.getDetail() + "\"")
                    .appendHtmlConstant("</span>");
        }
        if (entry.getCount() > 1) {
            builder.appendHtmlConstant("<span class=\"wbm-actions__count\">")
                    .append(entry.getCount())
                    .appendHtmlConstant("</span>");
        }
        return builder.toSafeHtml();
    }
}
