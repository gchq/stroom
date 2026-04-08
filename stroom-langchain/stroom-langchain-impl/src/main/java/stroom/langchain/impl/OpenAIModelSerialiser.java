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

package stroom.langchain.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.importexport.api.ImportExportDocument;
import stroom.openai.shared.OpenAIModelDoc;

import jakarta.inject.Inject;

import java.io.IOException;

public class OpenAIModelSerialiser implements DocumentSerialiser2<OpenAIModelDoc> {

    private final Serialiser2<OpenAIModelDoc> delegate;

    @Inject
    OpenAIModelSerialiser(
            final Serialiser2Factory serialiser2Factory
    ) {
        this.delegate = serialiser2Factory.createSerialiser(OpenAIModelDoc.class);
    }

    @Override
    public OpenAIModelDoc read(final ImportExportDocument importExportDocument) throws IOException {
        return delegate.read(importExportDocument);
    }

    @Override
    public ImportExportDocument write(final OpenAIModelDoc document) throws IOException {
        return delegate.write(document);
    }
}
