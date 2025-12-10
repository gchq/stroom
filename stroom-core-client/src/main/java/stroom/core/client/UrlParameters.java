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

package stroom.core.client;

import com.google.gwt.user.client.Window;

import javax.inject.Singleton;

@Singleton
public class UrlParameters {

    public static final String ACTION = "action";
    public static final String OPEN_DOC_ACTION = "open-doc";
    public static final String DOC_TYPE_QUERY_PARAM = "docType";
    public static final String DOC_UUID_QUERY_PARAM = "docUuid";

    private final boolean embedded;
    private final String title;
    private final String type;
    private final String uuid;
    private final String params;
    private final boolean queryOnOpen;
    private final String action;

    public UrlParameters() {
        this.embedded = Boolean.TRUE.toString().equalsIgnoreCase(Window.Location.getParameter("embedded"));
        this.title = Window.Location.getParameter("title");

        String type = Window.Location.getParameter("type");
        if (type == null) {
            // Alias
            type = Window.Location.getParameter(DOC_TYPE_QUERY_PARAM);
        }
        this.type = type;

        String uuid = Window.Location.getParameter("uuid");
        if (uuid == null) {
            // Alias
            uuid = Window.Location.getParameter(DOC_UUID_QUERY_PARAM);
        }
        this.uuid = uuid;

        this.params = Window.Location.getParameter("params");
        this.queryOnOpen = Boolean.TRUE.toString()
                .equalsIgnoreCase(Window.Location.getParameter("queryOnOpen"));
        this.action = Window.Location.getParameter(ACTION);
    }

    public boolean isEmbedded() {
        return embedded;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public String getUuid() {
        return uuid;
    }

    public String getParams() {
        return params;
    }

    public boolean isQueryOnOpen() {
        return queryOnOpen;
    }

    public String getAction() {
        return action;
    }
}
