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

package stroom.meta.api;

@FunctionalInterface
public interface AttributeMapper {

    AttributeMapper IDENTITY = attributeMap -> attributeMap;

    /**
     * Return an {@link AttributeMap} instance containing all entries from attributeMap.
     * Those values that need to be hashed will have been hashed.
     * If any values are hashed, a new {@link AttributeMap} will be returned.
     *
     * @param attributeMap Will not be modified but may be returned unchanged.
     */
    AttributeMap mapAttributes(final AttributeMap attributeMap);

    /**
     * @return An {@link AttributeMapper} that does not modify the {@link AttributeMap} in
     * any way.
     */
    static AttributeMapper identity() {
        return IDENTITY;
    }
}
