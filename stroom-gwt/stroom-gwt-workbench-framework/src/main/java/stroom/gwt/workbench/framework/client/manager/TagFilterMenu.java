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
import stroom.gwt.workbench.framework.client.tree.TagFilter;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// The sidebar's tag filter menu, opened by the filter button in the search box, as in React
/// Storybook: built in tags (Play), change detection tags (New, Modified, Related) and the
/// stories' own tags, each of which can be included or excluded.
public class TagFilterMenu {

    private static final String BUTTON_ID = "wbm-tag-filter";
    private static final String ATTR_TAG = "data-tag";
    private static final String ATTR_EXCLUDE = "data-exclude";
    private static final String ATTR_SELECT_ALL = "data-select-all";
    private static final String KIND_CUSTOM = "tag";

    private final TagFilter filter;
    private final Supplier<Map<String, Integer>> tagCounts;
    private final Runnable changeHandler;
    private final Element button;
    private Popover popover;

    /// @param filter        The filter the menu changes.
    /// @param tagCounts     Supplies the number of stories with each tag.
    /// @param changeHandler Called when the filter changes.
    public TagFilterMenu(final TagFilter filter,
                         final Supplier<Map<String, Integer>> tagCounts,
                         final Runnable changeHandler) {
        this.filter = filter;
        this.tagCounts = tagCounts;
        this.changeHandler = changeHandler;
        button = Document.get().getElementById(BUTTON_ID);
        BrowserUtil.addListener(button, "click", event ->
                popover = Popover.toggle(button, render(), Popover.Align.CENTER, this::onClick));
        updateButton();
    }

    private SafeHtml render() {
        final Map<String, Integer> counts = tagCounts.get();
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        final boolean active = filter.getActiveCount() > 0;
        builder.appendHtmlConstant("<div class=\"wbm-menu wbm-tag-menu\" role=\"dialog\" aria-label=\"Tag filters\">"
                                   + "<ul class=\"wbm-menu__group\"><li><button type=\"button\" "
                                   + "class=\"wbm-menu__button wbm-tag-menu__all\" " + ATTR_SELECT_ALL + "><span "
                                   + "class=\"wbm-menu__icon\">");
        MenuHtml.appendIcon(builder, "wbm-icon", active
                ? "wbm-icon-sweep"
                : "wbm-icon-batch-accept");
        builder.appendHtmlConstant("</span><span class=\"wbm-menu__label\">" + (active
                ? "Clear filters"
                : "Select all") + "</span></button></li></ul>");

        // As in Storybook, built in filters are only offered if some stories have the tag
        if (counts.getOrDefault(TagFilter.PLAY, 0) > 0) {
            builder.appendHtmlConstant("<ul class=\"wbm-menu__group\">");
            appendTag(builder, TagFilter.PLAY, "Play", "wbm-icon-play-hollow", "built-in", counts);
            builder.appendHtmlConstant("</ul>");
        }
        builder.appendHtmlConstant("<ul class=\"wbm-menu__group\">");
        appendTag(builder, TagFilter.NEW, "New", "wbm-icon-new", "status", counts);
        appendTag(builder, TagFilter.MODIFIED, "Modified", "wbm-icon-modified", "status", counts);
        // Storybook doesn't show an icon for related stories
        appendTag(builder, TagFilter.RELATED, "Related", null, "status", counts);
        builder.appendHtmlConstant("</ul>");

        final List<String> customTags = new ArrayList<>();
        for (final String tag : counts.keySet()) {
            if (TagFilter.isCustomTag(tag)) {
                customTags.add(tag);
            }
        }
        if (!customTags.isEmpty()) {
            builder.appendHtmlConstant("<ul class=\"wbm-menu__group\">");
            customTags.stream().sorted().forEach(tag -> appendTag(builder, tag, tag, null, KIND_CUSTOM, counts));
            builder.appendHtmlConstant("</ul>");
        }

        builder.appendHtmlConstant("</div>");
        return builder.toSafeHtml();
    }

    private void appendTag(final SafeHtmlBuilder builder,
                           final String tag,
                           final String label,
                           final String icon,
                           final String kind,
                           final Map<String, Integer> counts) {
        final boolean included = filter.isIncluded(tag);
        final boolean excluded = filter.isExcluded(tag);
        final String state = excluded
                ? " wbm-tag-menu__item--excluded"
                : included
                        ? " wbm-tag-menu__item--included"
                        : "";
        // Only the built in and status tags, which are constants, have their own icon style. Custom
        // tags come from the stories so must never be put into the markup unescaped.
        final String iconModifier = !KIND_CUSTOM.equals(kind) && HtmlTokens.isSafeToken(tag)
                ? " wbm-tag-menu__icon--" + tag
                : "";
        builder.appendHtmlConstant("<li class=\"wbm-tag-menu__item" + state + "\"><label class=\"wbm-menu__button\">"
                                   + "<span class=\"wbm-menu__icon wbm-tag-menu__icon" + iconModifier + "\">");
        // As in Storybook, the icon gives way to the checkbox when the tag is filtered on
        if (excluded) {
            MenuHtml.appendIcon(builder, "wbm-icon wbm-tag-menu__state-icon", "wbm-icon-delete");
        } else if (icon != null && !included) {
            MenuHtml.appendIcon(builder, "wbm-icon wbm-tag-menu__state-icon", icon);
        }
        builder.appendHtmlConstant("<input type=\"checkbox\"" + (included || excluded
                        ? " checked"
                        : "") + (icon == null
                        ? " class=\"wbm-tag-menu__checkbox--always\""
                        : "") + " " + ATTR_TAG + "=\"")
                .appendEscaped(tag)
                .appendHtmlConstant("\" aria-label=\"" + kind + " filter: " + (excluded
                        ? "exclude "
                        : ""))
                .appendEscaped(label)
                .appendHtmlConstant("\"></span><span class=\"wbm-menu__label\">")
                .appendEscaped(label);
        if (excluded) {
            builder.appendHtmlConstant("<span class=\"wbm-tag-menu__muted\"> (excluded)</span>");
        }
        builder.appendHtmlConstant("</span><span class=\"wbm-menu__right wbm-tag-menu__count\">")
                .appendHtmlConstant(excluded
                        ? "<s>"
                        : "")
                .append(counts.getOrDefault(tag, 0))
                .appendHtmlConstant((excluded
                        ? "</s>"
                        : "") + "</span></label><button type=\"button\" class=\"wbm-tag-menu__exclude\" "
                                    + ATTR_EXCLUDE + "=\"")
                .appendEscaped(tag)
                .appendHtmlConstant("\" aria-label=\"" + (excluded
                        ? "Include "
                        : "Exclude ") + kind + ": ")
                .appendEscaped(label)
                .appendHtmlConstant("\">" + (excluded
                        ? "Include"
                        : "Exclude") + "</button></li>");
    }

    private void onClick(final Element target) {
        final Element exclude = BrowserUtil.closest(target, "[" + ATTR_EXCLUDE + "]");
        final Element checkbox = BrowserUtil.closest(target, "input[" + ATTR_TAG + "]");
        if (exclude != null) {
            filter.toggleExcluded(exclude.getAttribute(ATTR_EXCLUDE));
        } else if (checkbox != null) {
            filter.toggleChecked(checkbox.getAttribute(ATTR_TAG));
        } else if (BrowserUtil.closest(target, "[" + ATTR_SELECT_ALL + "]") != null) {
            if (filter.getActiveCount() > 0) {
                filter.clear();
            } else {
                // As in Storybook, select all doesn't include the change statuses
                filter.includeAll(tagCounts.get().keySet()
                        .stream()
                        .filter(tag -> !TagFilter.isStatusTag(tag))
                        .collect(Collectors.toList()));
            }
        } else {
            return;
        }
        changed();
    }

    private void changed() {
        updateButton();
        changeHandler.run();
        if (popover != null && popover.getElement().getParentElement() != null) {
            popover.setContent(render());
        }
    }

    private void updateButton() {
        final int count = filter.getActiveCount();
        if (count > 0) {
            button.addClassName("wbm-icon-button--active");
            button.setAttribute("aria-label", count + " active tag filter" + (count == 1
                    ? ""
                    : "s"));
        } else {
            button.removeClassName("wbm-icon-button--active");
            button.setAttribute("aria-label", "Tag filters");
        }
    }
}
