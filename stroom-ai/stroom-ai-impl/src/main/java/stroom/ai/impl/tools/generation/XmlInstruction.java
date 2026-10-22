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

package stroom.ai.impl.tools.generation;

import java.util.Objects;

/// A processing instruction in an [XmlElement]'s children, as a parsed XSLT may hold one.
public final class XmlInstruction extends XmlNode {

    private final String target;
    private final String data;

    /// @param target The instruction's target.
    /// @param data   Its data, or null.
    public XmlInstruction(final String target, final String data) {
        this.target = Objects.requireNonNull(target, "target");
        this.data = data;
    }

    /// @return The instruction's target.
    public String target() {
        return target;
    }

    /// @return Its data, or null.
    public String data() {
        return data;
    }
}
