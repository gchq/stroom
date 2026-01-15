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

package stroom.docstore.api;

import stroom.util.shared.HasAuditInfo;

import java.util.function.Function;

public class AuditFieldFilter<D extends HasAuditInfo> implements Function<D, D> {

    @Override
    public D apply(final D doc) {
        doc.setCreateTimeMs(null);
        doc.setCreateUser(null);
        doc.setUpdateTimeMs(null);
        doc.setUpdateUser(null);
        return doc;
    }
}
