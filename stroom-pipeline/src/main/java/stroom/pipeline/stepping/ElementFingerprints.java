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

package stroom.pipeline.stepping;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * The computed config fingerprints for every element in a pipeline (see {@link ElementFingerprinter}).
 * <p>
 * An element's {@code ownFingerprint} covers only that element's own configuration. Its
 * {@code cumulativeFingerprint} folds in every upstream element's fingerprint, so it changes if and
 * only if the element or anything upstream of it changes - this is the key used to address the element's persisted
 * IO in the {@link StepDataStore}.
 */
public class ElementFingerprints {

    private final Map<String, String> ownFingerprints;
    private final Map<String, String> cumulativeFingerprints;

    ElementFingerprints(final Map<String, String> ownFingerprints,
                        final Map<String, String> cumulativeFingerprints) {
        this.ownFingerprints = Collections.unmodifiableMap(ownFingerprints);
        this.cumulativeFingerprints = Collections.unmodifiableMap(cumulativeFingerprints);
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
