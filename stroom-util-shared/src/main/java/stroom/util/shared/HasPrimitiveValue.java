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

package stroom.util.shared;

/// Something that can be represented by a primitive value, e.g. an enum that can be
/// represented as a byte value.
///
/// If you are going to use [PrimitiveValueConverter] then the range of primitive values should
/// be kept relatively small. This is to ensure that [PrimitiveValueConverter] can use its
/// faster sparse array backed [PrimitiveValueConverterArrayImpl] implementation, and if it does
/// use that then a smaller range means less memory usage.
///
/// Primitive values do not have to be contiguous, but if [PrimitiveValueConverterArrayImpl] is used
/// it benefits from less gaps in the range.
public interface HasPrimitiveValue {

    /// Returns the primitive value representation of this object.
    /// The value should ideally be in the range 0..100 if you are using [PrimitiveValueConverter].
    byte getPrimitiveValue();
}
