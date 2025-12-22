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

package stroom.hyperlink.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowDashboardEvent extends GwtEvent<ShowDashboardEvent.Handler> {

    private static Type<Handler> TYPE;

    private final Object context;
    private final String href;

    private ShowDashboardEvent(final Object context,
                               final String href) {
        this.context = context;
        this.href = href;
    }

    public static void fire(final HasHandlers handlers, final Object context, final String href) {
        handlers.fireEvent(new ShowDashboardEvent(context, href));
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

    public Object getContext() {
        return context;
    }

    public String getHref() {
        return href;
    }

    public interface Handler extends EventHandler {

        void onChange(ShowDashboardEvent event);
    }
}
