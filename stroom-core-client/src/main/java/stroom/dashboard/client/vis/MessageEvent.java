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

package stroom.dashboard.client.vis;

import com.google.gwt.core.client.JavaScriptObject;

public class MessageEvent extends JavaScriptObject {

    /**
     * Required constructor for GWT compiler to function.
     */
    protected MessageEvent() {
    }

    public final String getData() {
        return getData(this);
    }

    public final String getOrigin() {
        return getOrigin(this);
    }

    private final native String getData(MessageEvent evt)
    /*-{
    return evt.data;
    }-*/;

    private final native String getOrigin(MessageEvent evt)
    /*-{
    return evt.origin;
    }-*/;
}
