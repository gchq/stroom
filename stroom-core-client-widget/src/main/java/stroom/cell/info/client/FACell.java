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

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import stroom.widget.button.client.GlyphIcon;

public class FACell extends AbstractCell<GlyphIcon> {
    public interface Style extends CssResource {
        String DEFAULT_CSS = "FACell.css";

        String icon();

        String face();

        String disabled();
    }

    public interface Resources extends ClientBundle {
        @Source(Style.DEFAULT_CSS)
        Style style();
    }

    interface Template extends SafeHtmlTemplates {
        @Template("<div class=\"{0}\"><div class=\"{1}\" style=\"{2}\"><i class=\"{3}\"></i></div></div>")
        SafeHtml icon(String iconClassName, String faceClassName, SafeStyles colour, String icon);
    }

    private static Resources resources;
    private static Template template;

    public FACell() {
        super("click");
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final GlyphIcon value, final NativeEvent event,
                               final ValueUpdater<GlyphIcon> valueUpdater) {
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

    @Override
    protected void onEnterKeyDown(final Context context, final Element parent, final GlyphIcon value,
                                  final NativeEvent event, final ValueUpdater<GlyphIcon> valueUpdater) {
        if (valueUpdater != null) {
            valueUpdater.update(value);
        }
    }

    @Override
    public void render(final Context context, final GlyphIcon value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            sb.append(template.icon(resources.style().icon(), resources.style().face(), SafeStylesUtils.forTrustedColor(value.getColourSet().getEnabled()), value.getGlyph()));
        }
    }
}
