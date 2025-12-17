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

package stroom.cell.info.client;

import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;

public abstract class InfoColumn<T> extends Column<T, Preset> {

    public InfoColumn() {
        super(new SvgCell());
    }

    @Override
    public Preset getValue(final T object) {
        return SvgPresets.INFO;
    }

    protected abstract void showInfo(T row, PopupPosition popupPosition);

    @Override
    public void onBrowserEvent(final Context context, final Element elem, final T row, final NativeEvent event) {
        super.onBrowserEvent(context, elem, row, event);
        Element target = event.getEventTarget().cast();

        // Find the parent TD.
        while (target.getParentElement() != null &&
                !target.getTagName().equalsIgnoreCase("td") &&
                !target.getParentElement().getTagName().equalsIgnoreCase("td")) {
            target = target.getParentElement();
        }

        final Rect relativeRect = new Rect(target);
        final PopupPosition position = new PopupPosition(relativeRect, PopupLocation.RIGHT);
        showInfo(row, position);
    }
}
