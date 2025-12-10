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

package stroom.xmlschema;


import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestXMLSchemaStoreImpl extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestXMLSchemaStoreImpl.class);

    @Inject
    private XmlSchemaStore xmlSchemaStore;
    @Inject
    private CommonTestControl commonTestControl;

    @Test
    void test() {
        // Import the schemas.
        commonTestControl.createRequiredXMLSchemas();

        // Now make sure we can find a resource that we expect to be there.
        FindXMLSchemaCriteria criteria = new FindXMLSchemaCriteria();

        List<XmlSchemaDoc> list = xmlSchemaStore.find(criteria).getValues();
        assertThat(list).isNotNull();
        assertThat(list.size() > 1).isTrue();

        criteria = new FindXMLSchemaCriteria();
        criteria.setNamespaceURI("event-logging:3");
        list = xmlSchemaStore.find(criteria).getValues();

        assertThat(list)
                .isNotNull();

        LOGGER.info("Schemas:\n{}", list.stream()
                .map(xmlSchemaDoc ->
                        xmlSchemaDoc.getNamespaceURI() + " "
                                + xmlSchemaDoc.getSystemId() + " "
                                + xmlSchemaDoc.getSchemaGroup())
                .collect(Collectors.joining("\n")));

        assertThat(list.size())
                .isEqualTo(7);

        criteria = new FindXMLSchemaCriteria();
        criteria.setSystemId("file://event-logging-v3.0.0.xsd");
        list = xmlSchemaStore.find(criteria).getValues();
        assertThat(list)
                .isNotNull();
        assertThat(list.size())
                .isEqualTo(1);

        criteria = new FindXMLSchemaCriteria();
        criteria.setSchemaGroup("EVENTS");
        list = xmlSchemaStore.find(criteria).getValues();
        assertThat(list).isNotNull();
        assertThat(list.size())
                .isEqualTo(7);
    }
}
