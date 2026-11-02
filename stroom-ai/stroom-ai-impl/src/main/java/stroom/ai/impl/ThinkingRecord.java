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

package stroom.ai.impl;

/// The model's reasoning for one call, as stored (as JSON) in a THINKING message. Older THINKING messages hold
/// the reasoning as plain text.
///
/// @param text       The reasoning, as the model gave it.
/// @param durationMs How long the model spent thinking, or 0 if not known.
public record ThinkingRecord(String text,
                             long durationMs) {

}
