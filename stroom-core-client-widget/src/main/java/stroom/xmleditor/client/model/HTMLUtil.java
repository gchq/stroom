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

package stroom.xmleditor.client.model;

public final class HTMLUtil {
    private HTMLUtil() {
    }

    public static String htmlToText(String html) {
        // Replace <br> with \n.
        html = html.replaceAll("<n> </n>", "");
        html = html.replaceAll("<br/?>", "\n");
        html = html.replaceAll("<div>", "\n");

        // Remove all elements.
        html = html.replaceAll("<[^>]*>", "");
        // Now replace entity references to form proper XML.
        html = html.replaceAll("&lt;", "<");
        html = html.replaceAll("&gt;", ">");
        html = html.replaceAll("&nbsp;", " ");
        html = html.replaceAll("&amp;", "&");

        return html;
    }
}
