/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import net.sf.saxon.event.ReceivingContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Map;

public class FastInfosetContentHandler extends ReceivingContentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FastInfosetContentHandler.class);

    private final Map<String, String> uriToNewPrefixMap = new HashMap<>();

//    private static final Pattern PREFIX_PATTERN = Pattern.compile("\\w:");

    @Override
    public void startDocument() throws SAXException {
        LOGGER.trace("startDocument()");
//        super.startDocument();
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        LOGGER.trace("startPrefixMapping({}, {})", prefix, uri);
        String suggestedPrefix = super.getConfiguration().getNamePool().suggestPrefixForURI(uri);

        LOGGER.trace("suggestedPrefix {}", suggestedPrefix);

        if (suggestedPrefix != null) {
            if (suggestedPrefix.equals(prefix)) {
                // already have this prefix defined so do nothing.
            } else {
                // there is already a prefix for this uri and it differs from our so use theirs and store the new one
                uriToNewPrefixMap.putIfAbsent(uri, suggestedPrefix);
            }
        } else {
            // no suggested prefix so just use ours as is
//            super.startPrefixMapping(prefix, uri);
        }
    }



    @Override
    public void startElement(final String uri, final String localname, final String rawname, final Attributes atts) throws SAXException {
        LOGGER.trace("startElement {} {} {}", uri, localname, rawname);
//        String newRawName = changeRawNamePrefixIfRequired(uri, rawname);
//        super.startElement(uri, localname, newRawName, atts);
        super.startElement(uri, localname, rawname, atts);
    }

    @Override
    public void endElement(final String uri, final String localname, final String rawname) throws SAXException {
        LOGGER.trace("endElement {} {} {}", uri, localname, rawname);
//        String newRawName = changeRawNamePrefixIfRequired(uri, rawname);
//        super.endElement(uri, localname, newRawName);
        super.endElement(uri, localname, rawname);
    }

    @Override
    public void endDocument() throws SAXException {
        LOGGER.trace("endDocument()");
//        super.endDocument();
    }

//    private String changeRawNamePrefixIfRequired(final String uri, final String rawName) {
//        String newPrefix = uriToNewPrefixMap.get(uri);
//        LOGGER.trace("newPrefix {}", newPrefix);
//        String result;
//        if (newPrefix != null) {
//            String newRawName = PREFIX_PATTERN.matcher(rawName).replaceFirst(newPrefix + ":");
//
//            LOGGER.trace("Changing rawname from [{}] to [{}]", rawName, newRawName);
//            result = newRawName;
//        } else {
//            result = rawName;
//        }
//        return result;
//    }
}
