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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;

public interface StateLookupProvider {

    /**
     * <p>
     * Given a {@link LookupIdentifier} and a store doc ref, ensure that
     * the data required to perform the lookup is in the ref store. This method will not
     * perform the lookup, instead it will populate the {@link ReferenceDataResult} with
     * a proxy object that can later be used to do the lookup.
     * </p>
     *
     * @param docRef           A reference to the state doc.
     * @param lookupIdentifier The identifier to lookup in the reference data
     * @param result           The reference result object containing the proxy object for performing the lookup
     */
    void lookup(LookupIdentifier lookupIdentifier, ReferenceDataResult result);
}
