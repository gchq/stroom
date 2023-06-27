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

package stroom.cell.info.client;

import stroom.svg.client.Preset;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class SvgCell extends AbstractCell<Preset> {

    private static Template template;

    private final boolean isButton;

    public SvgCell() {
        this(true);
    }

    public SvgCell(final boolean isButton) {
        super(isButton
                ? "click"
                : null);
        this.isButton = isButton;

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final Preset value,
                               final NativeEvent event,
                               final ValueUpdater<Preset> valueUpdater) {
        if (isButton) {
            super.onBrowserEvent(context, parent, value, event, valueUpdater);
            if ("click".equals(event.getType())) {
                EventTarget eventTarget = event.getEventTarget();
                if (!Element.is(eventTarget)) {
                    return;
                }
                if (parent.getFirstChildElement().isOrHasChild(Element.as(eventTarget))) {
                    // Ignore clicks that occur outside of the main element.
                    onEnterKeyDown(context, parent, value, event, valueUpdater);
                }
            }
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final Preset value,
                                  final NativeEvent event,
                                  final ValueUpdater<Preset> valueUpdater) {
        if (isButton) {
            if (valueUpdater != null) {
                valueUpdater.update(value);
            }
        }
    }

    @Override
    public void render(final Context context, final Preset value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            String className = isButton
                    ? "svgCell-button"
                    : "svgCell-icon";

            if (!value.isEnabled()) {
                className += " " + "svgCell-disabled";
            }

            if (value.getTitle() != null && !value.getTitle().isEmpty()) {
                sb.append(template.icon(
                        className,
                        value.getTitle(),
                        SafeHtmlUtils.fromSafeConstant(value.getSvgImage().getSvg())));
            } else {
                sb.append(template.icon(
                        className,
                        SafeHtmlUtils.fromSafeConstant(value.getSvgImage().getSvg())));
            }
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml icon(String className, SafeHtml icon);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml icon(String className, String title, SafeHtml icon);
    }
}
