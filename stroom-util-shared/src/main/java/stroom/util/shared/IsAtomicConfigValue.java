/*
 * Copyright 2019 Crown Copyright
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


/// Marker interface to indicate that the implementing class should be treated
/// as an 'atomic' (not in the concurrency sense) stroom config/property value.
///
/// I.e. The object will likely have multiple fields, and possibly sub-objects,
/// but will be (de-)serialised as a single atomic unit. Thus, Stroom's property
/// key can reference this object, but not descend into it to refence its fields.
///
/// Typically implementing classes may be held in collections, e.g. lists, in which
/// case the whole collection is treated as an atomic unit.
///
/// Implementing classes **MUST** implement [Object#equals(Object)] as
/// equality is used to compare the property value with its default.
///
/// Implementing classes should **NOT** extend {@link AbstractConfig}
/// or implement the interfaces {@link IsStroomConfig} and {@link IsProxyConfig}. They
/// are intended for config branches.
///
/// Implementing classes **SHOULD** be annotated with {@link NotInjectableConfig} as they
/// represent a config value rather than a branch.
public interface IsAtomicConfigValue {

}
