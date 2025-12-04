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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.xml.converter.ds3.NodeFactory.NodeType;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

public class Root extends Node {
    private final boolean ignoreErrors;

    Root(final VarMap varMap, final RootFactory factory) {
        super(varMap, factory);
        ignoreErrors = factory.isIgnoreErrors();

        // Link the nodes together.
        for (final Node node : getChildNodes()) {
            node.link(varMap);
        }
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.ROOT;
    }

    @Override
    public boolean isExpression() {
        return false;
    }

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }
}
