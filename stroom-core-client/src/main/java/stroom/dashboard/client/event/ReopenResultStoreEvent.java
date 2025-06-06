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

package stroom.dashboard.client.event;

import stroom.query.api.ResultStoreInfo;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ReopenResultStoreEvent extends GwtEvent<ReopenResultStoreEvent.Handler> {

    private static Type<Handler> TYPE;

    private final ResultStoreInfo resultStoreInfo;

    private ReopenResultStoreEvent(final ResultStoreInfo resultStoreInfo) {
        this.resultStoreInfo = resultStoreInfo;
    }

    public static void fire(final HasHandlers handlers, final ResultStoreInfo resultStoreInfo) {
        handlers.fireEvent(new ReopenResultStoreEvent(resultStoreInfo));
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
        handler.onReopen(this);
    }

    public ResultStoreInfo getResultStoreInfo() {
        return resultStoreInfo;
    }

    public interface Handler extends EventHandler {

        void onReopen(ReopenResultStoreEvent event);
    }
}
