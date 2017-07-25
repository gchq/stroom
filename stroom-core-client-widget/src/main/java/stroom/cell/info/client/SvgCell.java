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
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.UriUtils;
import stroom.svg.client.SvgPreset;

public class SvgCell extends AbstractCell<SvgPreset> {
    private static Resources resources;
    private static Template template;

    public SvgCell() {
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
    public void onBrowserEvent(final Context context, final Element parent, final SvgPreset value, final NativeEvent event,
                               final ValueUpdater<SvgPreset> valueUpdater) {
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
    protected void onEnterKeyDown(final Context context, final Element parent, final SvgPreset value,
                                  final NativeEvent event, final ValueUpdater<SvgPreset> valueUpdater) {
        if (valueUpdater != null) {
            valueUpdater.update(value);
        }
    }

    @Override
    public void render(final Context context, final SvgPreset value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final SafeStylesBuilder builder = new SafeStylesBuilder();
            builder.append(SafeStylesUtils.forWidth(value.getWidth(), Unit.PX));
            builder.append(SafeStylesUtils.forHeight(value.getHeight(), Unit.PX));

            String className = resources.style().icon();
            if (!value.isEnabled()) {
                className += " " + resources.style().disabled();
            }

            sb.append(template.icon(className, builder.toSafeStyles(), UriUtils.fromString(value.getUrl())));
        }
    }

    public interface Style extends CssResource {
        String DEFAULT_CSS = "SvgCell.css";

        String icon();

        String face();

        String disabled();
    }

    public interface Resources extends ClientBundle {
        @Source(Style.DEFAULT_CSS)
        Style style();
    }

    interface Template extends SafeHtmlTemplates {
        @Template("<img class=\"{0}\" style=\"{1}\" src=\"{2}\"/>")
        SafeHtml icon(String className, SafeStyles style, SafeUri url);
    }
}
