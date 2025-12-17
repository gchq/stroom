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

package stroom.widget.xsdbrowser.client.view;

import stroom.widget.xsdbrowser.client.view.XSDNode.XSDAttribute;

import com.google.gwt.xml.client.CharacterData;
import com.google.gwt.xml.client.Node;

public final class XMLUtil {

    private XMLUtil() {
        // Utility class.
    }

    public static String removePrefix(final String qName) {
        String localName = qName;
        final int index = localName.lastIndexOf(":");
        if (index != -1) {
            localName = localName.substring(index + 1);
        }

        return localName;
    }

    public static String getAttributeValue(final Node node, final XSDAttribute attribute) {
        return getAttributeValue(node, attribute, true);
    }

    public static String getAttributeValue(final Node node, final XSDAttribute attribute, final boolean removePrefix) {
        String value = null;
        if (node != null && !(node instanceof CharacterData) && node.hasAttributes()) {
            final Node att = node.getAttributes().getNamedItem(attribute.toString());
            if (att != null) {
                if (removePrefix) {
                    value = removePrefix(att.getNodeValue());
                } else {
                    value = att.getNodeValue();
                }
            }
        }

        return value;
    }

    public static String getContent(final Node node) {
        final StringBuilder sb = new StringBuilder();

        if (node != null && node.hasChildNodes()) {
            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                final Node child = node.getChildNodes().item(i);

                final String text = child.getNodeValue();
                if (text != null) {
                    sb.append(text);
                }

                sb.append(getContent(child));
            }
        }

        return sb.toString();
    }
}
