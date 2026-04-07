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

package stroom.query.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.importexport.api.ImportExportDocument;
import stroom.query.shared.QueryDoc;

import jakarta.inject.Inject;

import java.io.IOException;

public class QuerySerialiser implements DocumentSerialiser2<QueryDoc> {

//    private static final Logger LOGGER = LoggerFactory.getLogger(QuerySerialiser.class);
//
//    private static final String JSON = "json";

    private final Serialiser2<QueryDoc> delegate;

    @Inject
    public QuerySerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(QueryDoc.class);
    }

    @Override
    public QueryDoc read(final ImportExportDocument importExportDocument) throws IOException {
        return delegate.read(importExportDocument);
    }

    @Override
    public ImportExportDocument write(final QueryDoc document) throws IOException {
        return delegate.write(document);
    }

}
