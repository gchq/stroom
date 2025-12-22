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

package stroom.alert.client.event;

import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.safehtml.shared.SafeHtml;

public abstract class CommonAlertEvent<H extends EventHandler> extends GwtEvent<H> {

    private final SafeHtml message;
    private final SafeHtml detail;
    private Level level = Level.WARN;

    protected CommonAlertEvent(final SafeHtml message) {
        this.message = message;
        this.detail = null;
    }

    protected CommonAlertEvent(final SafeHtml message, final SafeHtml detail, final Level level) {
        this.message = message;
        this.detail = detail;
        this.level = level;
    }

    protected CommonAlertEvent(final SafeHtml message, final Level level) {
        this.message = message;
        this.level = level;
        this.detail = null;
    }

    static SafeHtml fromString(final String string) {
        return SafeHtmlUtil.withLineBreaks(string);
    }

    public SafeHtml getMessage() {
        return message;
    }

    public Level getLevel() {
        return level;
    }

    public SafeHtml getDetail() {
        return detail;
    }

    public enum Level {
        INFO,
        QUESTION,
        WARN,
        ERROR
    }
}
