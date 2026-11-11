/*
 * Copyright 2026 Crown Copyright
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

package stroom.util.shared.diff;

/// The kind of difference between the old and new text for a block or line.
public enum DiffType {
    /// The line(s) are the same in both texts.
    EQUAL,
    /// The line(s) only exist in the new text.
    INSERT,
    /// The line(s) only exist in the old text.
    DELETE,
    /// The line(s) in the old text have been replaced by different line(s) in the new text.
    CHANGE
}
