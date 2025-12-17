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

package stroom.config.global.client.presenter;

import java.util.Objects;

public class NodeSource {

    private final String nodeName;
    private final String source;

    public NodeSource(final String nodeName,
                      final String source) {
        this.nodeName = Objects.requireNonNull(nodeName);
        this.source = Objects.requireNonNull(source);
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getSource() {
        return source;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeSource that = (NodeSource) o;
        return nodeName.equals(that.nodeName) &&
                source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeName, source);
    }

    @Override
    public String toString() {
        return "NodeSource{" +
                "nodeName='" + nodeName + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}
