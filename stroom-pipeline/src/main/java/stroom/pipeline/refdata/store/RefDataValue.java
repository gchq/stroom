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

package stroom.pipeline.refdata.store;

/**
 * Represents a reference data value in the store. The value could be one of
 * various types, e.g. simple string or fastinfoset serialised XML. The type
 * of the value is not know until it is read and its typeId is determined.
 * Each impl will have its own serde that can be used once the type is determined
 * by inspecting the serialised typeId.
 */
public interface RefDataValue {

    /**
     * @return The hashcode of just the underlying value that this object wraps
     * rather than hashcode of the whole object.
     */
    long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm);

    /**
     * @return A code to represent the class of the implementation,
     * unique within all sub-classes of {@link RefDataValue}. Stored in the DB as a single
     * byte so values must be in the range -128 to 127.
     */
    byte getTypeId();

    boolean isNullValue();
}
