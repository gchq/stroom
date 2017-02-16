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

package stroom.entity.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                final StringBuilder sb = new StringBuilder();
                sb.append(SYSPROP_SET_TO);
                sb.append(factoryName);
                sb.append(END);
                LOGGER.info(sb.toString());
            }

            final TransformerFactory factory = TransformerFactory.newInstance();
            final StringBuilder sb = new StringBuilder();
            sb.append(IMP_USED);
            sb.append(factory.getClass().getName());
            LOGGER.info(sb.toString());
        } catch (Exception ex) {
            LOGGER.error("Unable to create new TransformerFactory!", ex);
        }
    }

    private TransformerFactoryFactory() {
        // Utility class.
    }

    public static TransformerFactory newInstance() {
        return TransformerFactory.newInstance();
    }
}
