/*
 * Copyright 2016 Crown Copyright
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

package stroom.cache.shared;

import stroom.dispatch.shared.Action;
import stroom.node.shared.Node;
import stroom.util.shared.VoidResult;

public class CacheClearAction extends Action<VoidResult> {
    private static final long serialVersionUID = 6319893515607847166L;

    private String cacheName;
    private Node node;

    public CacheClearAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public CacheClearAction(final String cacheName, final Node node) {
        this.cacheName = cacheName;
        this.node = node;
    }

    public String getCacheName() {
        return cacheName;
    }

    public Node getNode() {
        return node;
    }

    @Override
    public String getTaskName() {
        return "Clear cache";
    }
}
