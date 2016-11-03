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

package stroom.widget.tab.client.event;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class TabSelectorEvent extends GwtEvent<TabSelectorHandler> {
    private static Type<TabSelectorHandler> TYPE;

    private final Element element;

    private TabSelectorEvent(final Element element) {
        this.element = element;
    }

    public static void fire(final HasHandlers source, final Element element) {
        source.fireEvent(new TabSelectorEvent(element));
    }

    public static Type<TabSelectorHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<TabSelectorHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final TabSelectorHandler handler) {
        handler.onClick(this);
    }

    public Element getElement() {
        return element;
    }
}
