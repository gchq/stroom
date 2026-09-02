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

import stroom.pipeline.stepping.store.StepDataStore;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The computed config fingerprints for every element in a pipeline (see {@link ElementFingerprinter}).
 * <p>
 * An element's {@code ownFingerprint} covers only that element's own configuration. Its
 * {@code cumulativeFingerprint} folds in every upstream element's fingerprint, so it changes if and
 * only if the element or anything upstream of it changes - this is the key used to address the element's persisted
 * IO in the {@link StepDataStore}.
 */
public class ElementFingerprints {

    private static final HashFunction SHA_256 = Hashing.sha256();

    private final Map<String, String> ownFingerprints;
    private final Map<String, String> cumulativeFingerprints;
    private final String signature;

    /**
     * Public so that callers outside this package - chiefly tests - can build a known set. In production
     * these come from {@link ElementFingerprinter}, which is the only thing that knows how to derive them.
     */
    public ElementFingerprints(final Map<String, String> ownFingerprints,
                               final Map<String, String> cumulativeFingerprints) {
        this.ownFingerprints = Collections.unmodifiableMap(ownFingerprints);
        this.cumulativeFingerprints = Collections.unmodifiableMap(cumulativeFingerprints);
        this.signature = computeSignature(this.cumulativeFingerprints);
    }

    /**
     * A single stable hash of every element's cumulative fingerprint - i.e. of the whole pipeline
     * configuration, including the user's unsaved code.
     * <p>
     * Two configurations share a signature exactly when every element's captured IO is interchangeable
     * between them, which is what makes it usable as a cache key: editing any element yields a new
     * signature, and reverting the edit yields the previous one back.
     */
    public String getSignature() {
        return signature;
    }

    private static String computeSignature(final Map<String, String> cumulativeFingerprints) {
        // Sorted so the signature does not depend on map iteration order.
        return SHA_256.hashString(
                new TreeMap<>(cumulativeFingerprints).toString(), StandardCharsets.UTF_8).toString();
    }

    public String getOwnFingerprint(final String elementId) {
        return ownFingerprints.get(elementId);
    }

    public String getCumulativeFingerprint(final String elementId) {
        return cumulativeFingerprints.get(elementId);
    }

    public Set<String> getElementIds() {
        return cumulativeFingerprints.keySet();
    }

    public Map<String, String> getCumulativeFingerprints() {
        return cumulativeFingerprints;
    }

    @Override
    public String toString() {
        return "ElementFingerprints{" +
                "cumulativeFingerprints=" + cumulativeFingerprints +
                '}';
    }
}
