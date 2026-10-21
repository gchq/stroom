/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.stepping;

import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.xslt.XsltStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/// The saved versions of the code (XSLT and text converters) a pipeline's elements run when no edited code is
/// injected for them. They are part of each element's fingerprint, so a step after the code is saved runs the
/// element again rather than serving what was captured with the code as it was.
///
/// Only code an element refers to by a property is seen: code found by a name pattern (e.g. from the feed's
/// name) isn't, nor is code it imports or includes.
@Singleton
public class SavedElementCode {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SavedElementCode.class);

    private final XsltStore xsltStore;
    private final TextConverterStore textConverterStore;

    @Inject
    public SavedElementCode(final XsltStore xsltStore,
                            final TextConverterStore textConverterStore) {
        this.xsltStore = xsltStore;
        this.textConverterStore = textConverterStore;
    }

    /// @param mergedPipelineData The merged pipeline data (see `PipelineDataMerger`).
    /// @param injectedCode       Element id to the edited code injected for it, which it runs instead of its
    ///                           saved code. May be null.
    /// @return Element id to the saved versions of the code it refers to, for the elements without injected
    /// code. An element whose code can't be read is left out, as running it fails anyway.
    public Map<String, String> versions(final PipelineData mergedPipelineData,
                                        final Map<String, String> injectedCode) {
        final Map<String, String> code = NullSafe.map(injectedCode);
        // Element id to its code documents' versions, by property name, so the result is the same whatever
        // order the properties come in.
        final Map<String, Map<String, String>> byElement = new HashMap<>();
        for (final PipelineProperty property : mergedPipelineData.getAddedProperties()) {
            final DocRef docRef = NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity);
            if (docRef != null && !code.containsKey(property.getElement())) {
                final String version = version(docRef);
                if (version != null) {
                    byElement.computeIfAbsent(property.getElement(), k -> new TreeMap<>())
                            .put(property.getName(), version);
                }
            }
        }
        final Map<String, String> versions = new HashMap<>();
        byElement.forEach((elementId, byProperty) -> versions.put(elementId, byProperty.toString()));
        return versions;
    }

    private String version(final DocRef docRef) {
        try {
            final AbstractDoc doc;
            if (XsltDoc.TYPE.equals(docRef.getType())) {
                doc = xsltStore.readDocument(docRef);
            } else if (TextConverterDoc.TYPE.equals(docRef.getType())) {
                doc = textConverterStore.readDocument(docRef);
            } else {
                return null;
            }
            return NullSafe.get(doc, AbstractDoc::getVersion);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read {} for its version: {}", docRef, e.getMessage()), e);
            return null;
        }
    }
}
