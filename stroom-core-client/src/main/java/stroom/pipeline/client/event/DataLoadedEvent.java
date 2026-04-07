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

package stroom.pipeline.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class DataLoadedEvent<T> extends GwtEvent<DataLoadedEvent.DataLoadedHandler<T>> {

    private static Type<DataLoadedHandler<?>> TYPE;
    private final T data;

    private DataLoadedEvent(final T data) {
        this.data = data;
    }

    public static <T> void fire(final HasDataLoadedHandlers<T> source, final T data) {
        source.fireEvent(new DataLoadedEvent<>(data));
    }

    public static Type<DataLoadedHandler<?>> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Type<DataLoadedHandler<T>> getAssociatedType() {
        return (Type) TYPE;
    }

    @Override
    protected void dispatch(final DataLoadedHandler<T> handler) {
        handler.onLoaded(this);
    }

    public T getData() {
        return data;
    }


    // --------------------------------------------------------------------------------


    public interface DataLoadedHandler<T> extends EventHandler {

        void onLoaded(DataLoadedEvent<T> event);
    }
}
