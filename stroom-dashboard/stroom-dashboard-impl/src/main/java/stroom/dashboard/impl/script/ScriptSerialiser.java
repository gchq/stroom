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

package stroom.dashboard.impl.script;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.script.shared.ScriptDoc;
import stroom.util.string.EncodingUtil;

import jakarta.inject.Inject;

import java.io.IOException;

public class ScriptSerialiser implements DocumentSerialiser2<ScriptDoc> {

    private static final String JS = "js";

    private final Serialiser2<ScriptDoc> delegate;

    @Inject
    public ScriptSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(ScriptDoc.class);
    }

    @Override
    public ScriptDoc read(final ImportExportDocument importExportDocument) throws IOException {
        ScriptDoc document = delegate.read(importExportDocument);
        final String js = EncodingUtil.asString(importExportDocument.getExtAssetData(JS));
        if (js != null) {
            document = document.copy().data(js).build();
        }
        return document;
    }

    @Override
    public ImportExportDocument write(final ScriptDoc document) throws IOException {
        final String js = document.getData();
        final ImportExportDocument importExportDocument = delegate.write(document.copy().data(null).build());
        if (js != null) {
            importExportDocument.addExtAsset(new ByteArrayImportExportAsset(JS, EncodingUtil.asBytes(js)));
        }
        return importExportDocument;
    }
}
