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
import stroom.pipeline.xslt.XsltImports;
import stroom.pipeline.xslt.XsltStore;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/// The saved versions of the code (XSLT and text converters) a pipeline's elements run when no edited code is
/// injected for them, and of the XSLTs that code imports or includes. They are part of each element's
/// fingerprint, so a step after the code (or an XSLT it imports) is saved runs the element again rather than
/// serving what was captured with the code as it was.
///
/// Imports are followed through any XSLTs they import in turn, and an import that can't be resolved is recorded
/// as such, so creating or renaming the XSLT it names also changes the fingerprint. Imports are resolved as the
/// compile resolves them (see [XsltImports]), as the processing user.
///
/// Only code an element refers to by a property is seen: code found by a name pattern (e.g. from the feed's
/// name) isn't.
@Singleton
public class SavedElementCode {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SavedElementCode.class);
    private static final String IMPORTS_SUFFIX = ".imports";
    // Guards against pathological import graphs; a real XSLT has a handful of imports at most.
    private static final int MAX_IMPORTS = 1_000;

    private final XsltStore xsltStore;
    private final TextConverterStore textConverterStore;
    private final XsltImports xsltImports;
    private final SecurityContext securityContext;

    @Inject
    public SavedElementCode(final XsltStore xsltStore,
                            final TextConverterStore textConverterStore,
                            final XsltImports xsltImports,
                            final SecurityContext securityContext) {
        this.xsltStore = xsltStore;
        this.textConverterStore = textConverterStore;
        this.xsltImports = xsltImports;
        this.securityContext = securityContext;
    }

    /// @param mergedPipelineData The merged pipeline data (see `PipelineDataMerger`).
    /// @param injectedCode       Element id to the edited code injected for it, which it runs instead of its
    ///                           saved code. May be null.
    /// @return Element id to the saved versions of the code it refers to, and of the XSLTs that code (or the
    /// injected code) imports. A document that can't be read is left out, as running it fails anyway.
    public Map<String, String> versions(final PipelineData mergedPipelineData,
                                        final Map<String, String> injectedCode) {
        final Map<String, String> code = NullSafe.map(injectedCode);
        // Element id to its code documents' versions, by property name, so the result is the same whatever
        // order the properties come in.
        final Map<String, Map<String, String>> byElement = new HashMap<>();
        for (final PipelineProperty property : mergedPipelineData.getAddedProperties()) {
            final DocRef docRef = NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity);
            if (docRef != null) {
                final String elementId = property.getElement();
                final String injected = code.get(elementId);
                final AbstractDoc doc = injected == null
                        ? read(docRef)
                        : null;
                if (doc != null && doc.getVersion() != null) {
                    byElement.computeIfAbsent(elementId, k -> new TreeMap<>())
                            .put(property.getName(), doc.getVersion());
                }
                if (XsltDoc.TYPE.equals(docRef.getType())) {
                    // The element runs the injected code if there is any, else the saved XSLT.
                    final String xslt;
                    if (injected != null) {
                        xslt = injected;
                    } else if (doc instanceof final XsltDoc xsltDoc) {
                        xslt = xsltDoc.getData();
                    } else {
                        xslt = null;
                    }
                    final String imports = importVersions(xslt);
                    if (imports != null) {
                        byElement.computeIfAbsent(elementId, k -> new TreeMap<>())
                                .put(property.getName() + IMPORTS_SUFFIX, imports);
                    }
                }
            }
        }
        final Map<String, String> versions = new HashMap<>();
        byElement.forEach((elementId, byProperty) -> versions.put(elementId, byProperty.toString()));
        return versions;
    }

    private AbstractDoc read(final DocRef docRef) {
        try {
            if (XsltDoc.TYPE.equals(docRef.getType())) {
                return xsltStore.readDocument(docRef);
            } else if (TextConverterDoc.TYPE.equals(docRef.getType())) {
                return textConverterStore.readDocument(docRef);
            } else {
                return null;
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read {} for its version: {}", docRef, e.getMessage()), e);
            return null;
        }
    }

    // How each import of the XSLT, and of the XSLTs it imports, resolves, in document order. Null if the XSLT
    // has no imports.
    private String importVersions(final String xslt) {
        if (!XsltImports.hasImports(xslt)) {
            return null;
        }
        final List<String> resolved = new ArrayList<>();
        securityContext.asProcessingUser(() -> addImports(xslt, new HashSet<>(), resolved));
        return resolved.toString();
    }

    private void addImports(final String xslt,
                            final Set<String> visitedUuids,
                            final List<String> resolved) {
        for (final String href : XsltImports.findHrefs(xslt)) {
            if (resolved.size() >= MAX_IMPORTS) {
                return;
            }
            final XsltImports.Resolution resolution;
            try {
                resolution = xsltImports.resolve(href);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to resolve import '{}': {}", href, e.getMessage()), e);
                resolved.add(href + "=unresolvable");
                continue;
            }
            if (resolution.isFound()) {
                final XsltDoc imported = resolution.xsltDoc();
                resolved.add(href + "=" + imported.getUuid() + "@" + imported.getVersion());
                // Follow each XSLT's imports once, so an import cycle can't recurse forever.
                if (visitedUuids.add(imported.getUuid())) {
                    addImports(imported.getData(), visitedUuids, resolved);
                }
            } else if (resolution.isAmbiguous()) {
                resolved.add(href + "=" + resolution.matchCount() + " matches");
            } else {
                resolved.add(href + "=not found");
            }
        }
    }
}
