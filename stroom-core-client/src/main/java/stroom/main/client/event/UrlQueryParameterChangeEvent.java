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

package stroom.main.client.event;

import stroom.main.client.event.UrlQueryParameterChangeEvent.UrlQueryParameterChangeHandler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Map;

public class UrlQueryParameterChangeEvent extends
        GwtEvent<UrlQueryParameterChangeHandler> {

    private static Type<UrlQueryParameterChangeHandler> TYPE;
    private final String action;
    private final Map<String, String> queryParams;

    private UrlQueryParameterChangeEvent(final String action, final Map<String, String> queryParams) {
        this.action = action;
        this.queryParams = queryParams;
    }

    public static void fire(final HasHandlers handlers, final String action, final Map<String, String> parameters) {
        handlers.fireEvent(new UrlQueryParameterChangeEvent(action, parameters));
    }

    public static Type<UrlQueryParameterChangeHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<UrlQueryParameterChangeHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final UrlQueryParameterChangeHandler handler) {
        handler.onChange(this);
    }

    public String getAction() {
        return action;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public interface UrlQueryParameterChangeHandler extends EventHandler {

        void onChange(UrlQueryParameterChangeEvent event);
    }
}
