/*
 * Copyright 2017 Crown Copyright
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

package stroom.core.db.migration._V07_00_00.docstore.shared;


import stroom.core.db.migration._V07_00_00.docref._V07_00_00_DocRef;

public final class _V07_00_00_DocRefUtil {
    private _V07_00_00_DocRefUtil() {
        // Utility class.
    }

    public static _V07_00_00_DocRef create(final _V07_00_00_Doc doc) {
        if (doc == null) {
            return null;
        }

        return new _V07_00_00_DocRef(doc.getType(), doc.getUuid(), doc.getName());
    }
}