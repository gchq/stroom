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

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.jaxp.SaxonTransformerFactory;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.CharacterMapExpander;
import net.sf.saxon.serialize.Emitter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SimpleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

public final class TransformerFactoryFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerFactoryFactory.class);

    private static final String SAXON_TRANSFORMER_FACTORY = "net.sf.saxon.TransformerFactoryImpl";
    private static final String IMP_USED = "The transformer factory implementation being used is: ";
    private static final String END = "\".";
    private static final String SYSPROP_SET_TO = "System property \"javax.xml.transform.TransformerFactory\" set to \"";
    private static final String SYSPROP_NOT_SET = "System property \"javax.xml.transform.TransformerFactory\" not set.";
    private static final String SYSPROP_TRANSFORMER_FACTORY = "javax.xml.transform.TransformerFactory";

    static {
        try {
            final String factoryName = System.getProperty(SYSPROP_TRANSFORMER_FACTORY);
            if (factoryName == null) {
                LOGGER.info(SYSPROP_NOT_SET);

                System.setProperty(SYSPROP_TRANSFORMER_FACTORY, SAXON_TRANSFORMER_FACTORY);
            } else {
                LOGGER.info(SYSPROP_SET_TO + factoryName + END);
            }

            final TransformerFactory factory = newInstance(false);
            LOGGER.info(IMP_USED + factory.getClass().getName());
        } catch (final Exception ex) {
            LOGGER.error("Unable to create new TransformerFactory!", ex);
        }
    }

    private TransformerFactoryFactory() {
        // Utility class.
    }

    public static TransformerFactory newInstance(final boolean preventEscapeSwitching) {
        final TransformerFactory factory = TransformerFactory.newInstance();
        secureProcessing(factory);

        final SaxonTransformerFactory saxonTransformerFactory = (SaxonTransformerFactory) factory;
        final Configuration configuration = saxonTransformerFactory.getConfiguration();
        configuration.setSerializerFactory(new MySerializerFactory(configuration, preventEscapeSwitching));

        return factory;
    }

    private static void secureProcessing(final TransformerFactory factory) {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (final TransformerConfigurationException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static class MySerializerFactory extends SerializerFactory {

        private final boolean preventEscapeSwitching;

        public MySerializerFactory(final Configuration config,
                                   final boolean preventEscapeSwitching) {
            super(config);
            this.preventEscapeSwitching = preventEscapeSwitching;
        }

        @Override
        protected Emitter newXMLEmitter(final Properties properties) {
            return new MyXmlEmitter(preventEscapeSwitching);
        }

        @Override
        public CharacterMapExpander newCharacterMapExpander(final Receiver next) {
            return new NulStrippingCharacterMapExpander(next);
        }
    }

    // Saxon marks the text a character map substitutes with U+0000 so the emitter writes it unescaped.
    // A U+0000 already in the data would be indistinguishable from those markers, so this removes them
    // before the character map is applied. XML cannot hold U+0000 in any form so nothing is lost.
    private static class NulStrippingCharacterMapExpander extends CharacterMapExpander {

        public NulStrippingCharacterMapExpander(final Receiver next) {
            super(next);
        }

        @Override
        public void characters(final CharSequence chars,
                               final Location locationId,
                               final int properties) throws XPathException {
            super.characters(MyXmlEmitter.removeNul(chars), locationId, properties);
        }

        @Override
        public void attribute(final NodeName nameCode,
                              final SimpleType typeCode,
                              final CharSequence value,
                              final Location locationId,
                              final int properties) throws XPathException {
            super.attribute(nameCode, typeCode, MyXmlEmitter.removeNul(value), locationId, properties);
        }
    }
}
