/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.shared;


/**
 * Marker interface to indicate that the implementing class should be treated
 * as an 'atomic' (not in the concurrency sense) stroom config/property value.
 * <p>
 * I.e. The object will likely have multiple fields, and possibly sub-objects,
 * but will be (de-)serialised as a single atomic unit. Thus, Stroom's property
 * key can reference this object, but not descend into it to refence its fields.
 * </p>
 * <p>
 * Typically implementing classes may be held in collections, e.g. lists, in which
 * case the whole collection is treated as an atomic unit.
 * </p>
 * <p>
 * Implementing classes <strong>MUST</strong> implement {@link Object#equals(Object)} as
 * equality is used to compare the property value with its default.
 * with
 * </p>
 *
 */
public interface IsAtomicConfig extends IsStroomConfig {

}
