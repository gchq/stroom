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

package stroom.pipeline.xmlschema;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.util.shared.ResultPage;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class XmlSchemaStoreImpl
        extends AbstractDocumentStore<XmlSchemaDoc>
        implements XmlSchemaStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlSchemaStoreImpl.class);

    @Inject
    public XmlSchemaStoreImpl(final StoreFactory storeFactory,
                              final XmlSchemaSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                XmlSchemaDoc.TYPE,
                XmlSchemaDoc::builder,
                XmlSchemaDoc::copy);
    }

    @Override
    public ResultPage<XmlSchemaDoc> find(final FindXMLSchemaCriteria criteria) {
        final List<XmlSchemaDoc> result = new ArrayList<>();

        final List<DocRef> docRefs = list();
        docRefs.forEach(docRef -> {
            try {
                final XmlSchemaDoc doc = readDocument(docRef);
                if (criteria.matches(doc)) {
                    result.add(doc);
                }

            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        });
        return ResultPage.createCriterialBasedList(result, criteria);
    }
}
