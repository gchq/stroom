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

package stroom.meta.impl;

import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.util.shared.Flushable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MetaValueDao extends Flushable {

    void addAttributes(Meta meta, AttributeMap attributes);

    Map<Long, Map<String, String>> getAttributes(List<Meta> list);

    void deleteOldValues();

    int delete(Collection<Long> metaIdList);
}
