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

package stroom.util.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.SAXParserFactory;

/**
 * Our own factory class to return the desired SAXParserFactory implementation. This is due to the many xerces
 * implementations available, e.g. the internal one in the JDK, and the one bundled in gwt-dev. This class reports the
 * value of the javax.xml.parsers.SAXParserFactory property which is used by SAXParserFactory to determine which
 * implementation to return. If the property is not set then we hard code it to the xerces implementation in the JRE.
 */
public final class SAXParserFactoryFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAXParserFactoryFactory.class);

    private static final String DEFAULT_SAX_PARSER_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl";
    private static final String IMP_USED = "The SAX Parser factory implementation being used is: ";
    private static final String END = "\".";
    private static final String SYSPROP_SAX_PARSER_FACTORY = "javax.xml.parsers.SAXParserFactory";
    private static final String SYSPROP_SET_TO = "System property \"" + SYSPROP_SAX_PARSER_FACTORY + "\" set to \"";
    private static final String SYSPROP_NOT_SET = "System property \"" + SYSPROP_SAX_PARSER_FACTORY + "\" not set.";

    static {
        try {
            final String factoryName = System.getProperty(SYSPROP_SAX_PARSER_FACTORY);
            if (factoryName == null) {
                LOGGER.info(SYSPROP_NOT_SET);

                System.setProperty(SYSPROP_SAX_PARSER_FACTORY, DEFAULT_SAX_PARSER_FACTORY);
            } else {
                final StringBuilder sb = new StringBuilder();
                sb.append(SYSPROP_SET_TO);
                sb.append(factoryName);
                sb.append(END);
                LOGGER.info(sb.toString());
            }

            final SAXParserFactory factory = SAXParserFactory.newInstance();
            final StringBuilder sb = new StringBuilder();
            sb.append(IMP_USED);
            sb.append(factory.getClass().getName());
            LOGGER.info(sb.toString());
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to configure SAXParserFactory!", e);
        }
    }

    private SAXParserFactoryFactory() {
        // Utility class.
    }

    public static SAXParserFactory newInstance() {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        return factory;
    }
}
