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

package stroom.main.client.view;

import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class DynamicStyles {

    private static Element styleElement;

    private static Map<SafeHtml, SafeStyles> styles = new HashMap<>();

    public static Element getStyleElement() {
        if (styleElement == null) {
            styleElement = DOM.createElement("style");
            RootPanel.getBodyElement().appendChild(styleElement);
        }
        return styleElement;
    }

    public static void put(final SafeHtml className, final SafeStyles safeStyles) {
        styles.put(className, safeStyles);
        update();
    }

    public static void remove(final SafeHtml className) {
        styles.remove(className);
        update();
    }

    private static void update() {
        final HtmlBuilder builder = new HtmlBuilder();
        for (final Entry<SafeHtml, SafeStyles> entry : styles.entrySet()) {
            builder.append(entry.getKey());
            builder.appendTrustedString(" {\n");
            builder.appendTrustedString(entry.getValue().asString());
            builder.appendTrustedString("\n}\n\n");
        }
        getStyleElement().setInnerSafeHtml(builder.toSafeHtml());
    }
}
