/*
 * Copyright 2016 Crown Copyright
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

import stroom.util.shared.Expander;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.UriUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ExpanderCell extends AbstractCell<Expander> {

    private static final Set<String> ENABLED_EVENTS = new HashSet<>(Arrays.asList("click", "keydown"));
    private static volatile Template template;

    public ExpanderCell() {
        super(ENABLED_EVENTS);
        if (template == null) {
            synchronized (ExpanderCell.class) {
                if (template == null) {
                    template = GWT.create(Template.class);
                }
            }
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
            final int padding = depth * 10;
            final SafeStyles style = SafeStylesUtils.fromTrustedString("padding-left:" + padding + "px;");
            String className = "";
            SafeHtml icon = null;

            if (value.isLeaf()) {
                icon = template.icon(
                        "expanderCell-expanderIcon",
                        UriUtils.fromTrustedString(
                                "images/tree-leaf.svg"));
//                icon = getImageHtml(resources.leaf());
            } else if (value.isExpanded()) {
                icon = template.icon(
                        "expanderCell-expanderIcon",
                        UriUtils.fromTrustedString(
                                "images/tree-open.svg"));
//                icon = getImageHtml(resources.open());
                className = "expanderCell-active";
            } else {
                icon = template.icon(
                        "expanderCell-expanderIcon",
                        UriUtils.fromTrustedString(
                                "images/tree-closed.svg"));
//                icon = getImageHtml(resources.closed());
                className = "expanderCell-active";
            }

            sb.append(template.outerDiv(className, style, icon));

        } else {
            sb.append(SafeHtmlUtils.fromSafeConstant("<br/>"));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml outerDiv(String className, SafeStyles style, SafeHtml icon);

        @Template("<img class=\"{0}\" src=\"{1}\" />")
        SafeHtml icon(String iconClass, SafeUri iconUrl);
    }
}
