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

package stroom.importexport.server;

import stroom.entity.shared.DocRef;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public abstract class ImportContentHandler extends DefaultHandler {
    int depth = 0;

    private StringBuilder content = new StringBuilder();
    private DocRef docRef;

    abstract void handleAttribute(String property, Object value);

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);
        if (depth == 3 && localName.equals("doc")) {
            docRef = new DocRef();
        }

        content.setLength(0);
        depth++;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        super.endElement(uri, localName, qName);
        if (depth == 2) {
            if (docRef != null) {
                handleAttribute(localName, docRef);
                docRef = null;
            } else {
                handleAttribute(localName, content.toString());
            }
        } else if (docRef != null) {
            if ("type".equals(localName)) {
                docRef.setType(content.toString());
            } else if ("uuid".equals(localName)) {
                docRef.setUuid(content.toString());
            } else if ("name".equals(localName)) {
                docRef.setName(content.toString());
            }
        }

        content.setLength(0);
        depth--;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);
        content.append(ch, start, length);
    }
}
