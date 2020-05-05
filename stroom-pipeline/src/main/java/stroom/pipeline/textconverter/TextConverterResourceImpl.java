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
 */

package stroom.pipeline.textconverter;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterResource;

import javax.inject.Inject;

class TextConverterResourceImpl implements TextConverterResource {
    private final TextConverterStore textConverterStore;
    private final DocumentResourceHelper documentResourceHelper;

    @Inject
    TextConverterResourceImpl(final TextConverterStore textConverterStore,
                              final DocumentResourceHelper documentResourceHelper) {
        this.textConverterStore = textConverterStore;
        this.documentResourceHelper = documentResourceHelper;
    }

    @Override
    public TextConverterDoc read(final DocRef docRef) {
        return documentResourceHelper.read(textConverterStore, docRef);
    }

    @Override
    public TextConverterDoc update(final TextConverterDoc doc) {
        return documentResourceHelper.update(textConverterStore, doc);
    }
}