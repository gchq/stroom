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

package stroom.importexport.impl;

import stroom.docref.DocRef;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public abstract class ImportContentHandler extends DefaultHandler {

    int depth = 0;

    private final StringBuilder content = new StringBuilder();
    private String type;
    private String uuid;
    private String name;
    private boolean inDoc;

    abstract void handleAttribute(String property, Object value);

    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String qName,
                             final Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);
        if (depth == 3 && localName.equals("doc")) {
            type = null;
            uuid = null;
            name = null;
            inDoc = true;
        }

        content.setLength(0);
        depth++;
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        super.endElement(uri, localName, qName);
        if (depth == 2) {
            if (inDoc) {
                handleAttribute(localName, new DocRef(type, uuid, name));
                type = null;
                uuid = null;
                name = null;
                inDoc = false;
            } else {
                handleAttribute(localName, content.toString());
            }
        } else if (inDoc) {
            if ("type".equals(localName)) {
                type = content.toString();
            } else if ("uuid".equals(localName)) {
                uuid = content.toString();
            } else if ("name".equals(localName)) {
                name = content.toString();
            }
        }

        content.setLength(0);
        depth--;
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
        content.append(ch, start, length);
    }
}
