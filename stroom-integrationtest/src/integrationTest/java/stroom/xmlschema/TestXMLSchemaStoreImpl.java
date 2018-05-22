/*
 * Copyright 2017 Crown Copyright
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

package stroom.xmlschema;

import org.junit.Assert;
import org.junit.Test;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XmlSchemaDoc;

import javax.inject.Inject;
import java.util.List;

public class TestXMLSchemaStoreImpl extends AbstractCoreIntegrationTest {
    @Inject
    private XmlSchemaStore xmlSchemaStore;
    @Inject
    private CommonTestControl commonTestControl;

    @Test
    public void test() {
        // Import the schemas.
        commonTestControl.createRequiredXMLSchemas();

        // Now make sure we can find a resource that we expect to be there.
        FindXMLSchemaCriteria criteria = new FindXMLSchemaCriteria();

        List<XmlSchemaDoc> list = xmlSchemaStore.find(criteria);
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 1);

        criteria = new FindXMLSchemaCriteria();
        criteria.setNamespaceURI("event-logging:3");
        list = xmlSchemaStore.find(criteria);
        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());

        criteria = new FindXMLSchemaCriteria();
        criteria.setSystemId("file://event-logging-v3.0.0.xsd");
        list = xmlSchemaStore.find(criteria);
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        criteria = new FindXMLSchemaCriteria();
        criteria.setSchemaGroup("EVENTS");
        list = xmlSchemaStore.find(criteria);
        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());
    }
}
