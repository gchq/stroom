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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import stroom.query.api.v2.DocRef;

public class ShowDashboardEvent extends GwtEvent<ShowDashboardEvent.Handler> {
    private static Type<Handler> TYPE;
    private final String title;
    private final DocRef docRef;
    private final String params;

    public ShowDashboardEvent(final String title, final DocRef docRef, final String params) {
        this.title = title;
        this.docRef = docRef;
        this.params = params;
    }

    public static void fire(final HasHandlers handlers, final String title, final DocRef docRef, final String params) {
        handlers.fireEvent(new ShowDashboardEvent(title, docRef, params));
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
        handler.onChange(this);
    }

    public String getTitle() {
        return title;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getParams() {
        return params;
    }

    public interface Handler extends EventHandler {
        void onChange(ShowDashboardEvent event);
    }
}
