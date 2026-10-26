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

package stroom.ai.impl.extraction;

import stroom.ai.impl.tools.MergedPipeline;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// The pipelines a dashboard table's search extraction runs in: which element of one writes its records, and the
/// pipeline a new extraction is tried in before one is made.
public final class ExtractionPipelines {

    /// The content pack's Search Extraction template, which an extraction pipeline Stroom makes inherits.
    public static final String SEARCH_EXTRACTION_TEMPLATE_UUID = "3d9d60e9-61c2-4c88-a57b-7bc584dd970e";
    /// The XSLT element of the Search Extraction template, and of the pipeline a new extraction is tried in.
    public static final String XSLT_ELEMENT = "xsltFilter";
    /// The types of element that read an extraction's records into a table.
    static final Set<String> OUTPUT_TYPES = Set.of("SearchResultOutputFilter", "DynamicSearchResultOutputFilter");
    static final String XSLT_FILTER = "XSLTFilter";

    private ExtractionPipelines() {
        // Static only.
    }

    /// Finds the element of an extraction pipeline whose XSLT writes its records: the last XSLT before the element
    /// that reads them into the table.
    ///
    /// @param pipeline An extraction pipeline, merged with what it inherits.
    /// @return The XSLT element's id; empty if the pipeline has no such element (e.g. it isn't an extraction
    /// pipeline).
    public static Optional<String> outputXslt(final MergedPipeline pipeline) {
        final Map<String, String> elements = pipeline.elements();
        String xslt = null;
        for (final String element : pipeline.chain()) {
            final String type = elements.get(element);
            if (OUTPUT_TYPES.contains(type)) {
                return Optional.ofNullable(xslt);
            }
            if (XSLT_FILTER.equals(type)) {
                xslt = element;
            }
        }
        return Optional.empty();
    }

    /// The XSLT an element of a pipeline uses.
    ///
    /// @param pipeline A pipeline, merged with what it inherits.
    /// @param element  An XSLT element's id.
    /// @return Its XSLT; empty if it has none.
    public static Optional<DocRef> xsltOf(final MergedPipeline pipeline, final String element) {
        final Object value = pipeline.properties().get(new MergedPipeline.ElementProperty(element, "xslt"));
        if (value instanceof final Map<?, ?> map && map.get("uuid") != null) {
            return Optional.of(new DocRef(String.valueOf(map.get("type")), String.valueOf(map.get("uuid")),
                    map.get("name") == null
                            ? null
                            : String.valueOf(map.get("name"))));
        }
        return Optional.empty();
    }

    /// A pipeline to try a new extraction in, unsaved: an Events stream parsed, split into events, each given its
    /// stream and event ids, and written as records by [#XSLT_ELEMENT], as the Search Extraction template does. The
    /// XSLT is given as code when stepped; stepping only takes code for an element whose XSLT is set, so it is set
    /// to one the user can view, whose own code isn't used.
    ///
    /// @param placeholder An XSLT the user can view.
    /// @return The pipeline.
    public static PipelineDoc trial(final DocRef placeholder) {
        final PipelineDataBuilder data = new PipelineDataBuilder()
                .addElement(PipelineDataUtil.createElement("Source", "Source", null, null))
                .addElement(PipelineDataUtil.createElement("xmlParser", "XMLParser", null, null))
                .addElement(PipelineDataUtil.createElement("splitFilter", "SplitFilter", null, null))
                .addElement(PipelineDataUtil.createElement("idEnrichmentFilter", "IdEnrichmentFilter", null, null))
                .addElement(PipelineDataUtil.createElement(XSLT_ELEMENT, XSLT_FILTER, null, null))
                .addProperty(new PipelineProperty("splitFilter", "splitDepth", new PipelinePropertyValue(1)))
                // One event a record, as each is stepped on its own.
                .addProperty(new PipelineProperty("splitFilter", "splitCount", new PipelinePropertyValue(1)))
                .addProperty(PipelineDataUtil.createProperty(XSLT_ELEMENT, "xslt", placeholder));
        for (final List<String> link : List.of(List.of("Source", "xmlParser"), List.of("xmlParser", "splitFilter"),
                List.of("splitFilter", "idEnrichmentFilter"), List.of("idEnrichmentFilter", XSLT_ELEMENT))) {
            data.addLink(link.get(0), link.get(1));
        }
        return PipelineDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Extraction trial")
                .pipelineData(data.build())
                .build();
    }
}
