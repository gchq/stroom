/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.floormap.shared.FloorMapDoc;
import stroom.importexport.api.ImportExportDocument;

import jakarta.inject.Inject;

import java.io.IOException;

/// Handles serialisation and deserialisation of [FloorMapDoc] documents
/// for the document store.
///
/// Delegates to a [Serialiser2] instance created by the [Serialiser2Factory].
public class FloorMapSerialiser implements DocumentSerialiser2<FloorMapDoc> {

    private final Serialiser2<FloorMapDoc> delegate;

    @Inject
    public FloorMapSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(FloorMapDoc.class);
    }

    @Override
    public FloorMapDoc read(final ImportExportDocument importExportDocument) throws IOException {
        return delegate.read(importExportDocument);
    }

    @Override
    public ImportExportDocument write(final FloorMapDoc document) throws IOException {
        return delegate.write(document);
    }
}
