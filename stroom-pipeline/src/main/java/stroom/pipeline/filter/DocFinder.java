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

package stroom.pipeline.filter;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docref.HasFindDocsByName;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.shared.AbstractDoc;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DocFinder<D extends AbstractDoc> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocFinder.class);

    private final String type;
    private final PathCreator pathCreator;
    private final HasFindDocsByName hasFindDocsByName;
    private final DocRefInfoService docRefInfoService;

    public DocFinder(final String type,
                     final PathCreator pathCreator,
                     final HasFindDocsByName hasFindDocsByName,
                     final DocRefInfoService docRefInfoService) {
        this.type = type;
        this.pathCreator = pathCreator;
        this.hasFindDocsByName = hasFindDocsByName;
        this.docRefInfoService = docRefInfoService;
    }

    public DocRef findDoc(final DocRef defaultRef,
                          final String namePattern,
                          final String feedName,
                          final String pipelineName,
                          final Consumer<String> errorConsumer,
                          final boolean suppressNotFoundWarnings) {
        DocRef doc = null;

        // In case defaultRef has an out of date name (possible if the name is stored in pipeline source)
        // update it. Also possible for defaultRef to be a broken dependency, i.e. the doc has been deleted.
        final DocRef updatedDefaultRef = Optional.ofNullable(defaultRef)
                .flatMap(docRef -> docRefInfoService.info(docRef).map(DocRefInfo::getDocRef))
                .orElse(null);

        // Load the document from a name pattern if one has been specified.
        if (NullSafe.isNonBlankString(namePattern)) {
            // Resolve replacement variables.
            String resolvedName = namePattern.trim();
            if (feedName != null) {
                resolvedName = pathCreator.replace(resolvedName, "feed", () -> feedName);
            }
            if (pipelineName != null) {
                resolvedName = pathCreator.replace(resolvedName, "pipeline", () -> pipelineName);
            }

            LOGGER.debug("defaultRef: {}, updatedDefaultRef: {}, namePattern: '{}', resolvedName: '{}'",
                    defaultRef, updatedDefaultRef, namePattern, resolvedName);

            // Make sure there are no replacement vars left.
            final String[] vars = pathCreator.findVars(resolvedName);
            if (vars.length > 0) {
                final StringBuilder sb = new StringBuilder()
                        .append(type)
                        .append(" name pattern \"")
                        .append(namePattern)
                        .append("\" contains invalid replacement variables (");
                for (final String var : vars) {
                    sb.append(var)
                            .append(", ");
                }
                sb.setLength(sb.length() - 2);
                sb.append(")");
                throw ProcessException.create(sb.toString());
            }

            final List<DocRef> docs = hasFindDocsByName.findByName(resolvedName);
            if (NullSafe.isEmptyCollection(docs)) {
                if (errorConsumer != null && !suppressNotFoundWarnings) {
                    final StringBuilder sb = new StringBuilder()
                            .append("No ")
                            .append(type)
                            .append(" found with name '")
                            .append(resolvedName)
                            .append("' from pattern '")
                            .append(namePattern);

                    if (updatedDefaultRef != null) {
                        sb.append("' - using default ")
                                .append(type)
                                .append(" ")
                                .append(docRefToString(updatedDefaultRef));
                    } else {
                        sb.append("' - no default ")
                                .append(type)
                                .append(" specified");
                    }

                    errorConsumer.accept(sb.toString());
                }
            } else {
                doc = docs.get(0);
                if (errorConsumer != null && docs.size() > 1) {
                    final String message = "" +
                                           "Found " + docs.size()
                                           + " " + type + "s with name '" +
                                           resolvedName +
                                           "' from pattern '" +
                                           namePattern +
                                           "' - using " + type + " " + docRefToString(doc) +
                                           ". All matching UUIDs: " +
                                           LogUtil.truncate(
                                                   docs.stream().map(DocRef::getUuid).collect(Collectors.joining(", ")),
                                                   300) +
                                           ")";
                    errorConsumer.accept(message);
                }
            }
        }

        // Load the document from a reference if we haven't found it by name.
        if (doc == null) {
            // defaultRef provided, but does not exist
            if (defaultRef != null && updatedDefaultRef == null) {
                final String message = type + " "
                                       + docRefToString(defaultRef)
                                       + " appears to have been deleted";
                throw ProcessException.create(message);
            }
            doc = updatedDefaultRef;
        }

        return doc;
    }

    private String docRefToString(final DocRef docRef) {
        return docRef == null
                ? ""
                : "'" + docRef.getName() + "' (" + docRef.getUuid() + ")";
    }
}
