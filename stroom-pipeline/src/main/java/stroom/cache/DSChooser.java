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

package stroom.cache;

import org.xml.sax.ErrorHandler;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.io.StreamUtil;
import stroom.xml.converter.ParserFactory;
import stroom.xml.converter.ds3.DS3ParserFactory;
import stroom.xml.converter.xmlfragment.XMLFragmentParserFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.StringReader;

public class DSChooser {
    private static final String DATA_SPLITTER_ELEMENT = "<dataSplitter";
    private static final String DATA_SPLITTER_2_ELEMENT = "<dataSplitter";
    private static final String VERSION_ATTRIBUTE = "version=\"";
    private static final String VERSION2 = "2.0";
    private static final String VERSION3 = "3.0";

    private final Provider<DS3ParserFactory> parserFactoryProvider;

    @Inject
    public DSChooser(final Provider<DS3ParserFactory> parserFactoryProvider) {
        this.parserFactoryProvider = parserFactoryProvider;
    }

    public ParserFactory configure(final String xml, final ErrorHandler errorHandler) {
        if (xml.contains(DATA_SPLITTER_2_ELEMENT)) {
            final String version = getVersion(xml);

            // if (VERSION2.equals(version)) {
            // final DS2ParserFactory dataSplitterParserFactory =
            // beanStore.getBean(DS2ParserFactory.class);
            // dataSplitterParserFactory.configure(new StringReader(xml),
            // errorHandler);
            // return dataSplitterParserFactory;
            //
            // } else

            if (VERSION3.equals(version)) {
                final DS3ParserFactory dataSplitterParserFactory = parserFactoryProvider.get();
                dataSplitterParserFactory.configure(new StringReader(xml), errorHandler);
                return dataSplitterParserFactory;

            } else {
                throw new ProcessException("Unknown data splitter version \"" + version + "\" in XML configuration");
            }

        }
//        else {
//            throw new ProcessException("Unable to determine text converter type from XML configuration");

//        }

        return XMLFragmentParserFactory.create(StreamUtil.stringToStream(xml), errorHandler);
    }

    private String getVersion(final String xml) {
        final int start = xml.indexOf(DATA_SPLITTER_2_ELEMENT);
        if (start != -1) {
            final int end = xml.indexOf(">", start);
            if (end != -1) {
                int versionStart = xml.indexOf(VERSION_ATTRIBUTE, start);
                if (versionStart != -1 && versionStart < end) {
                    versionStart += VERSION_ATTRIBUTE.length();

                    final int versionEnd = xml.indexOf("\"", versionStart);
                    if (versionEnd != -1 && versionEnd < end) {
                        return xml.substring(versionStart, versionEnd);
                    }
                }
            }
        }

        return null;
    }
}
