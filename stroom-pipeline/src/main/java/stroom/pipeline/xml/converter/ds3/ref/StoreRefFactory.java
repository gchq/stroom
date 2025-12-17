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

package stroom.pipeline.xml.converter.ds3.ref;

import stroom.pipeline.xml.converter.ds3.Store;
import stroom.pipeline.xml.converter.ds3.StoreNode;

public class StoreRefFactory extends RefFactory {
    private final String refId;
    private final int refGroup;
    private final MatchIndex matchIndex;
    private boolean local;

    public StoreRefFactory(final String reference, final String section, final String refId, final int refGroup,
                           final MatchIndex matchIndex) {
        super(reference, section);
        this.refId = refId;
        this.refGroup = refGroup;
        this.matchIndex = matchIndex;
    }

    public Ref createRef(final VarMap varMap, final StoreNode owner) {
        // Find referenced store.
        StoreNode storeNode = owner;
        if (refId != null) {
            storeNode = varMap.getVar(refId);
        }

        final Store store = storeNode.getStore(refGroup, local);
        return new StoreRef(this, store);
    }

    @Override
    public boolean isText() {
        return false;
    }

    // Used for testing.
    String getRefId() {
        return refId;
    }

    // Used for testing.
    int getRefGroup() {
        return refGroup;
    }

    MatchIndex getMatchIndex() {
        return matchIndex;
    }

    // Used for testing.
    boolean isLocal() {
        return local;
    }

    public void setLocal(final boolean local) {
        this.local = local;
    }
}
