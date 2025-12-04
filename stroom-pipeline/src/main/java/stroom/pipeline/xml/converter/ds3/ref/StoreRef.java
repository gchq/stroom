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

import stroom.pipeline.xml.converter.ds3.Buffer;
import stroom.pipeline.xml.converter.ds3.Store;

public class StoreRef implements Ref {
    private final StoreRefFactory factory;
    private final Store store;

    public StoreRef(final StoreRefFactory factory, final Store store) {
        this.factory = factory;
        this.store = store;
    }

    public Buffer lookup(final int matchCount) {
        if (factory.getMatchIndex() != null) {
            if (factory.getMatchIndex().isOffset()) {
                return store.get(matchCount + factory.getMatchIndex().getIndex());
            } else {
                return store.get(factory.getMatchIndex().getIndex());
            }
        }

        return store.get(matchCount);
    }

    @Override
    public String toString() {
        return factory.toString();
    }
}
