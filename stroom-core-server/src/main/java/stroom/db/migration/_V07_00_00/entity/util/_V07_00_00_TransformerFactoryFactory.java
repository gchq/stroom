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

package stroom.db.migration._V07_00_00.entity.util;

import net.sf.saxon.Configuration;
import net.sf.saxon.jaxp.SaxonTransformerFactory;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.serialize.Emitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.util.MyXmlEmitter;

import javax.xml.XMLConstants;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import java.util.Properties;

public final class _V07_00_00_TransformerFactoryFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(_V07_00_00_TransformerFactoryFactory.class);

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

            final TransformerFactory factory = newInstance();
            LOGGER.info(IMP_USED + factory.getClass().getName());
        } catch (Exception ex) {
            LOGGER.error("Unable to create new TransformerFactory!", ex);
        }
    }

    private _V07_00_00_TransformerFactoryFactory() {
        // Utility class.
    }

    public static TransformerFactory newInstance() {
        final TransformerFactory factory = TransformerFactory.newInstance();
        secureProcessing(factory);

        final SaxonTransformerFactory saxonTransformerFactory = (SaxonTransformerFactory) factory;
        final Configuration configuration = saxonTransformerFactory.getConfiguration();
        configuration.setSerializerFactory(new MySerializerFactory(configuration));

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
        public MySerializerFactory(final Configuration config) {
            super(config);
        }

        @Override
        protected Emitter newXMLEmitter(final Properties properties) {
            return new MyXmlEmitter();
        }
    }
}