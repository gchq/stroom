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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.util.client.FieldEnd;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// Stories for the fields with something drawn over their end (an icon box's calendar or drop-down
/// arrow, as a time or schedule box's clock, a password box's button and badge, a quick filter's buttons, a spinner's
/// arrows): each holds a value too long for it, and its text must stop at least [FieldEnd#GAP]
/// pixels before what is over its end, never running under it.
public final class FieldEndStories {

    private static final String LONG = "A value far too long to fit in the box beside what is over its end";
    // Each field's block, so the play can find each field and what is over its end
    private static final String[] BLOCKS = {
            "date-time-box", "selection-box", "password-box", "password-box--with-badge",
            "quick-filter", "quick-filter--recent", "value-spinner"};

    private FieldEndStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/FieldEnds", FieldEndStories.class)
                .layout(StoryLayout.PADDED)
                // Every field's text stops before what is over its end, even when Clear has appeared in
                // a quick filter, or a badge beside a password box's button
                .story("TextStaysClearOfTheEnd", context -> fields())
                .withPlay(play -> {
                    for (final String block : BLOCKS) {
                        play.expect(block + " overflows (so the check means something)",
                                () -> overflows(play.querySelector("." + block + "-check").element().get()))
                                .toBe(true);
                        play.expect(block + ": pixels between its text and what is over its end",
                                () -> endGap(play.querySelector("." + block + "-check").element().get()))
                                .toBeGreaterThanOrEqual(FieldEnd.GAP);
                    }
                });
    }

    private static Widget fields() {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("display", "grid");
        panel.getElement().getStyle().setProperty("gap", "12px");
        panel.getElement().getStyle().setProperty("width", "200px");

        // Only its layout is checked, so its calendar opens no dialog (no controller is attached)
        final DateTimeBox dateTime = new DateTimeBox();
        dateTime.setExpressionsAllowed(true);
        dateTime.setText(LONG);
        panel.add(named(dateTime, "Date and time", "date-time-box"));

        final SelectionBox<String> selection = new SelectionBox<>();
        selection.addItem(LONG);
        selection.setValue(LONG);
        panel.add(named(selection, "Selection", "selection-box"));

        final PasswordBox password = new PasswordBox();
        password.setValue(LONG);
        password.setShown(true);
        panel.add(named(password, "Password", "password-box"));

        final PasswordBox badged = new PasswordBox();
        badged.setValue(LONG);
        badged.setShown(true);
        final SimplePanel badge = new SimplePanel();
        badge.setStyleName("change-password__length change-password__length--short");
        badge.getElement().setInnerText("9+");
        badged.setBadge(badge);
        panel.add(named(badged, "Password with a badge", "password-box--with-badge"));

        // Text in the box, so Clear is shown as well as the help
        final QuickFilter filter = new QuickFilter();
        filter.setText(LONG, false);
        panel.add(named(filter, "Filter", "quick-filter"));

        final QuickFilter recent = new QuickFilter();
        recent.setContextHandler(new QuickFilterContextHandler() {
            @Override
            public void showRecentFilters(final QuickFilter quickFilter, final Consumer<String> onSelect) {
                // No recent filters in this story
            }

            @Override
            public void recordUse(final String filterText) {
                // Not recorded in this story
            }
        });
        recent.setText(LONG, false);
        panel.add(named(recent, "Filter with recent filters", "quick-filter--recent"));

        final ValueSpinner spinner = new ValueSpinner();
        spinner.setMax(Long.MAX_VALUE);
        spinner.setValue(1234567890123456789L);
        panel.add(named(spinner, "Spinner", "value-spinner"));
        return panel;
    }

    // Names the field and marks it for the play (a class of the story's own, not one of Stroom's)
    private static Widget named(final Widget widget, final String name, final String block) {
        AccessibleName.set(widget, name);
        widget.addStyleName(block + "-check");
        return widget;
    }

    // The field's text: an input, or a selection box's picture of its value
    private static native Element text(Element root) /*-{
        return root.querySelector('.selection-box__value') || root.querySelector('input');
    }-*/;

    /// @return Whether the field's text is wider than the room it has (so it reaches its end).
    private static boolean overflows(final Element root) {
        final Element text = text(root);
        return text.getScrollWidth() > text.getClientWidth();
    }

    /// @return The pixels between the end of the field's text (its content box) and the start of what
    /// is drawn over its end.
    private static native double endGap(Element root) /*-{
        var text = root.querySelector('.selection-box__value') || root.querySelector('input');
        var end = root.querySelector('.icon-box__icon, .password-box__end, .quick-filter__toolbar,'
                                     + ' .value-spinner__steps');
        var t = text.getBoundingClientRect();
        var cs = $wnd.getComputedStyle(text);
        var contentRight = t.right - parseFloat(cs.borderRightWidth) - parseFloat(cs.paddingRight);
        return Math.round(end.getBoundingClientRect().left - contentRight);
    }-*/;
}
