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
import stroom.svg.client.SvgPresets;

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

import java.util.function.BiConsumer;

public class ActionCell<R> extends AbstractCell<R> {

    private static Template template;
    private final Preset svgPreset;
    private final BiConsumer<R, NativeEvent> action;

    public ActionCell(final BiConsumer<R, NativeEvent> action) {
        this(SvgPresets.ELLIPSES_HORIZONTAL, action);
    }

    public ActionCell(final Preset svgPreset,
                      final BiConsumer<R, NativeEvent> action) {
        super("click");
        this.action = action;
        this.svgPreset = svgPreset;

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final R row,
                               final NativeEvent event,
                               final ValueUpdater<R> valueUpdater) {
        super.onBrowserEvent(context, parent, row, event, valueUpdater);
        if ("click".equals(event.getType())) {
            EventTarget eventTarget = event.getEventTarget();
            if (!Element.is(eventTarget)) {
                return;
            }
            if (parent.getFirstChildElement().isOrHasChild(Element.as(eventTarget))) {
                // Ignore clicks that occur outside of the main element.
                onEnterKeyDown(context, parent, row, event, valueUpdater);
            }
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final R row,
                                  final NativeEvent event,
                                  final ValueUpdater<R> valueUpdater) {
        action.accept(row, event);

        if (valueUpdater != null) {
            valueUpdater.update(row);
        }
    }

    @Override
    public void render(final Context context,
                       final R row,
                       final SafeHtmlBuilder sb) {
        if (row == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String className = "svgCell-button " + svgPreset.getClassName();

            if (svgPreset.getTitle() != null && !svgPreset.getTitle().isEmpty()) {
                sb.append(template.icon(
                        className,
                        svgPreset.getTitle()));
            } else {
                sb.append(template.icon(
                        className));
            }
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\"></div>")
        SafeHtml icon(String className);

        @Template("<div class=\"{0}\" title=\"{1}\"></div>")
        SafeHtml icon(String className, String title);
    }
}
