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

package stroom.content.client.event;

import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class OpenContentTabEvent extends GwtEvent<OpenContentTabEvent.OpenContentTabHandler> {

    private static Type<OpenContentTabHandler> TYPE;
    private final TabData tabData;
    private final Layer layer;
    private final MyPresenterWidget<?> presenter;
    private final Consumer<MyPresenterWidget<?>> callbackOnOpen;

    private OpenContentTabEvent(final TabData tabData, final Layer layer, final MyPresenterWidget<?> presenter,
                                final Consumer<MyPresenterWidget<?>> callbackOnOpen) {
        this.tabData = tabData;
        this.layer = layer;
        this.presenter = presenter;
        this.callbackOnOpen = callbackOnOpen;
    }

    public static void fire(final HasHandlers handlers, final TabData tabData, final Layer layer,
            final MyPresenterWidget<?> presenter, final Consumer<MyPresenterWidget<?>> callbackOnOpen) {
        handlers.fireEvent(new OpenContentTabEvent(tabData, layer, presenter, callbackOnOpen));
    }

    public static Type<OpenContentTabHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenContentTabHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenContentTabHandler handler) {
        handler.onOpen(this);
    }

    public TabData getTabData() {
        return tabData;
    }

    public Layer getLayer() {
        return layer;
    }

    public void runCallbackOnOpen() {
        if (presenter != null && callbackOnOpen != null) {
            callbackOnOpen.accept(presenter);
        }
    }


    // --------------------------------------------------------------------------------


    public interface OpenContentTabHandler extends EventHandler {

        void onOpen(OpenContentTabEvent event);
    }
}
