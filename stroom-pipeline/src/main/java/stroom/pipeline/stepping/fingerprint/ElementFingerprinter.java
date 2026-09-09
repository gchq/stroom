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

package stroom.pipeline.stepping.fingerprint;

import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Computes a stable config fingerprint for each element of a merged pipeline, used to content-address
 * persisted stepping IO and to decide what to reprocess when a pipeline is edited.
 * <p>
 * {@code ownFingerprint} = SHA-256 of a deterministic (consistent property order) descriptor of the
 * element: its id, type, its {@link PipelineProperty} rows, its {@link PipelineReference}s and any
 * in-session injected code. {@code cumulativeFingerprint} = SHA-256 of the (ordered) cumulative
 * fingerprints of the element's upstream neighbours plus its own fingerprint, so it changes if and
 * only if the element or anything upstream of it changes.
 */
@Singleton
public class ElementFingerprinter {

    // Guava's SHA-256 HashFunction is immutable and thread-safe, so it can be reused across threads
    // without the per-call MessageDigest.getInstance lookup (MessageDigest itself is stateful and must
    // not be shared).
    private static final HashFunction SHA_256 = Hashing.sha256();

    /**
     * <p>PRECONDITION: {@code mergedPipelineData} must be fully merged (see {@code PipelineDataMerger}).
     * Only the effective/added element/property/reference/link lists are read; the remove-lists are
     * ignored, so passing a non-merged {@link PipelineData} would yield wrong fingerprints (and hence
     * silent stale-IO reuse).</p>
     *
     * @param mergedPipelineData the flattened/merged pipeline data (see {@code PipelineDataMerger}).
     * @param injectedCode       elementId -&gt; in-session edited code (XSLT/DataSplitter etc.), may be null.
     * @throws IllegalArgumentException if the pipeline contains a cycle.
     */
    public ElementFingerprints fingerprint(final PipelineData mergedPipelineData,
                                           final Map<String, String> injectedCode) {
        final Map<String, String> code = NullSafe.map(injectedCode);

        // Index elements by id.
        final Map<String, PipelineElement> elementsById = new HashMap<>();
        for (final PipelineElement element : mergedPipelineData.getAddedElements()) {
            elementsById.put(element.getId(), element);
        }

        // Group properties and references by owning element id.
        final Map<String, List<PipelineProperty>> propertiesByElement = new HashMap<>();
        for (final PipelineProperty property : mergedPipelineData.getAddedProperties()) {
            propertiesByElement.computeIfAbsent(property.getElement(), k -> new ArrayList<>()).add(property);
        }
        final Map<String, List<PipelineReference>> referencesByElement = new HashMap<>();
        for (final PipelineReference reference : mergedPipelineData.getAddedPipelineReferences()) {
            referencesByElement.computeIfAbsent(reference.getElement(), k -> new ArrayList<>()).add(reference);
        }

        // Build the upstream adjacency (to -> [from...]). Only keep links between real elements so a
        // dangling link can't inject a phantom (element-less) id into the fingerprint maps.
        final Map<String, List<String>> upstreamByElement = new HashMap<>();
        for (final PipelineLink link : mergedPipelineData.getAddedLinks()) {
            if (elementsById.containsKey(link.getFrom()) && elementsById.containsKey(link.getTo())) {
                upstreamByElement.computeIfAbsent(link.getTo(), k -> new ArrayList<>()).add(link.getFrom());
            }
        }

        // Own fingerprints.
        final Map<String, String> ownFingerprints = new HashMap<>();
        for (final PipelineElement element : elementsById.values()) {
            ownFingerprints.put(element.getId(), computeOwnFingerprint(
                    element,
                    propertiesByElement.get(element.getId()),
                    referencesByElement.get(element.getId()),
                    code.get(element.getId())));
        }

        // Cumulative fingerprints (memoised, cycle-guarded).
        final Map<String, String> cumulativeFingerprints = new HashMap<>();
        for (final String elementId : elementsById.keySet()) {
            computeCumulativeFingerprint(
                    elementId, upstreamByElement, ownFingerprints, cumulativeFingerprints, new HashSet<>());
        }

        return new ElementFingerprints(ownFingerprints, cumulativeFingerprints);
    }

    private String computeOwnFingerprint(final PipelineElement element,
                                         final List<PipelineProperty> properties,
                                         final List<PipelineReference> references,
                                         final String injectedCode) {
        final List<PipelineProperty> sortedProperties = new ArrayList<>(NullSafe.list(properties));
        sortedProperties.sort(Comparator.naturalOrder());
        final List<PipelineReference> sortedReferences = new ArrayList<>(NullSafe.list(references));
        sortedReferences.sort(Comparator.naturalOrder());

        // TreeMap + the consistent-order mapper give a deterministic descriptor irrespective of map or
        // nested-object property order. JsonUtil warns the consistent-order mapper is test-oriented due to
        // a sorting cost, but that is negligible here: descriptors are tiny and serialised at most once per
        // element per stepping request, and deterministic order is required for a stable fingerprint.
        final Map<String, Object> descriptor = new TreeMap<>();
        descriptor.put("id", element.getId());
        descriptor.put("type", element.getType());
        descriptor.put("properties", sortedProperties);
        descriptor.put("references", sortedReferences);
        descriptor.put("code", injectedCode);

        final String json = JsonUtil.writeValueAsConsistentString(descriptor, false);
        return sha256Hex(json);
    }

    private String computeCumulativeFingerprint(final String elementId,
                                                final Map<String, List<String>> upstreamByElement,
                                                final Map<String, String> ownFingerprints,
                                                final Map<String, String> cumulativeFingerprints,
                                                final Set<String> visiting) {
        final String existing = cumulativeFingerprints.get(elementId);
        if (existing != null) {
            return existing;
        }

        final String own = ownFingerprints.getOrDefault(elementId, "");

        // A valid pipeline is a DAG. If we re-enter an element we're already visiting there is a cycle;
        // fail loudly rather than produce an order-dependent (non-deterministic) fingerprint.
        if (!visiting.add(elementId)) {
            throw new IllegalArgumentException(
                    "Pipeline contains a cycle involving element '" + elementId + "'");
        }

        final List<String> upstreamIds = new ArrayList<>(NullSafe.list(upstreamByElement.get(elementId)));
        // Combine upstream cumulative fingerprints in a stable (sorted) order.
        final List<String> upstreamFingerprints = new ArrayList<>();
        for (final String upstreamId : upstreamIds) {
            upstreamFingerprints.add(computeCumulativeFingerprint(
                    upstreamId, upstreamByElement, ownFingerprints, cumulativeFingerprints, visiting));
        }
        upstreamFingerprints.sort(Comparator.naturalOrder());

        visiting.remove(elementId);

        final Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("upstream", upstreamFingerprints);
        descriptor.put("own", own);
        final String cumulative = sha256Hex(JsonUtil.writeValueAsConsistentString(descriptor, false));

        cumulativeFingerprints.put(elementId, cumulative);
        return cumulative;
    }

    private static String sha256Hex(final String value) {
        if (value == null) {
            // JsonUtil logs and swallows serialisation failures, returning null; surface that clearly
            // rather than NPE, as a null descriptor would otherwise corrupt the fingerprint.
            throw new IllegalStateException("Unable to serialise element descriptor for fingerprinting");
        }
        // HashCode.toString() is lower-case hex, which the store relies on when using it as a filename.
        return SHA_256.hashString(value, StandardCharsets.UTF_8).toString();
    }
}
