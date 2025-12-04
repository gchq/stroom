/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.cell.expander.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.Expander;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ExpanderCell extends AbstractCell<Expander> {

    private static final int PADDING = 5;
    private static final int ICON_WIDTH = 20;
    private static final Set<String> ENABLED_EVENTS = new HashSet<>(
            Arrays.asList(BrowserEvents.CLICK, BrowserEvents.KEYDOWN));
    private static volatile Template template;
    private final int initialOffset;

    public ExpanderCell() {
        this(0);
    }

    public ExpanderCell(final int initialOffset) {
        super(ENABLED_EVENTS);

        this.initialOffset = initialOffset;

        if (template == null) {
            synchronized (ExpanderCell.class) {
                if (template == null) {
                    template = GWT.create(Template.class);
                }
            }
        }
    }

    /**
     * Get the maximum column width required to fit expander icons for all levels
     */
    public static int getColumnWidth(final int maxDepth) {
        if (maxDepth >= 0) {
            return (maxDepth * ICON_WIDTH) + ICON_WIDTH + PADDING;
        } else {
            return 0;
        }
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final Expander value,
                               final NativeEvent event, final ValueUpdater<Expander> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if ("click".equals(event.getType())) {
            final EventTarget eventTarget = event.getEventTarget();
            if (!Element.is(eventTarget)) {
                return;
            }
            if (parent.getFirstChildElement().isOrHasChild(Element.as(eventTarget))) {
                // Ignore clicks that occur outside of the main element.
                onEnterKeyDown(context, parent, value, event, valueUpdater);
            }
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context, final Element parent, final Expander value,
                                  final NativeEvent event, final ValueUpdater<Expander> valueUpdater) {
        if (valueUpdater != null && value != null && !value.isLeaf()) {
            valueUpdater.update(value);
        }
    }

    @Override
    public void render(final Context context, final Expander value, final SafeHtmlBuilder sb) {
        if (value != null) {
            final int depth = value.getDepth();
            final int padding = (depth * ICON_WIDTH) + initialOffset;
            final SafeStyles style = SafeStylesUtils.fromTrustedString("padding-left:" + padding + "px;");
            String className = "";
            final SvgImage expanderIcon;
            if (value.isLeaf()) {
                expanderIcon = SvgImage.DOT;
            } else if (value.isExpanded()) {
                expanderIcon = SvgImage.ARROW_DOWN;
                className += " active";
            } else {
                expanderIcon = SvgImage.ARROW_RIGHT;
                className += " active";
            }

            final SafeHtml iconSafeHtml = SvgImageUtil.toSafeHtml(
                    expanderIcon,
                    "expanderIcon" + className);
            sb.append(template.expander(
                    "expanderCell",
                    style,
                    iconSafeHtml));

        } else {
            sb.append(SafeHtmlUtils.fromSafeConstant("<br/>"));
        }
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String expanderClass, SafeStyles styles, SafeHtml icon);
    }
}
