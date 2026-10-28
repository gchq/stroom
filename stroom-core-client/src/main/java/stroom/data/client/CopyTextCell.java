/*
 * Copyright 2024 Crown Copyright
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

package stroom.data.client;

import stroom.widget.datagrid.client.EventCell;
import stroom.widget.util.client.Clipboard;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.view.client.CellPreviewEvent;

/// A text cell with a hover icon that copies its text to the clipboard ([Clipboard#writeText]).
public class CopyTextCell extends AbstractCell<String> implements EventCell {

    /// Makes a cell that copies its text when its icon is pressed.
    public CopyTextCell() {
        super(BrowserEvents.MOUSEDOWN);
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (BrowserEvents.MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 5);
        }
        return false;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final String value,
                               final NativeEvent event,
                               final ValueUpdater<String> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if (BrowserEvents.MOUSEDOWN.equals(event.getType())) {
            if (MouseUtil.isPrimary(event)) {
                onEnterKeyDown(context, parent, value, event, valueUpdater);
            }
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final String value,
                                  final NativeEvent event,
                                  final ValueUpdater<String> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        if (ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 5)) {
            CopyTextUtil.keepSelection(event);
            if (value != null) {
                Clipboard.writeText(value);
            }
        }
    }

    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        CopyTextUtil.render(value, new HtmlBuilder(sb), false);
    }
}
