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

package stroom.data.client.presenter;

import stroom.pipeline.shared.SourceLocation;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowDataEvent extends GwtEvent<ShowDataEvent.Handler> {

    private static Type<ShowDataEvent.Handler> TYPE;

    private final SourceLocation sourceLocation;
    private final DataViewType dataViewType;
    private final DisplayMode displayMode;

    private ShowDataEvent(final SourceLocation sourceLocation,
                          final DataViewType dataViewType,
                          final DisplayMode displayMode) {
        this.sourceLocation = sourceLocation;
        this.dataViewType = dataViewType;
        this.displayMode = displayMode;
    }


    public static void fire(final HasHandlers source,
                            final SourceLocation sourceLocation,
                            final DataViewType dataViewType,
                            final DisplayMode displayMode) {
        source.fireEvent(new ShowDataEvent(sourceLocation, dataViewType, displayMode));
    }

    public static void fire(final HasHandlers source,
                            final SourceLocation sourceLocation) {
        source.fireEvent(new ShowDataEvent(
                sourceLocation,
                DataViewType.PREVIEW,
                DisplayMode.DIALOG));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onShow(this);
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public DataViewType getDataViewType() {
        return dataViewType;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface Handler extends EventHandler {

        void onShow(ShowDataEvent event);
    }
}
