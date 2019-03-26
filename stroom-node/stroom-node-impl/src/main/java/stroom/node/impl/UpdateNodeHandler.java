/*
 * Copyright 2018 Crown Copyright
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

package stroom.node.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.node.shared.Node;
import stroom.node.shared.UpdateNodeAction;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class UpdateNodeHandler extends AbstractTaskHandler<UpdateNodeAction, Node> {
    private final NodeServiceImpl nodeService;
    private final DocumentEventLog entityEventLog;

    @Inject
    UpdateNodeHandler(final NodeServiceImpl nodeService,
                      final DocumentEventLog entityEventLog) {
        this.nodeService = nodeService;
        this.entityEventLog = entityEventLog;
    }

    @Override
    public Node exec(final UpdateNodeAction action) {
        final Node node = action.getNode();
        Node result;
        Node before = null;

        try {
            // Get the before version.
            before = nodeService.getNode(node.getName());
            result = nodeService.update(node);
            entityEventLog.update(before, result, null);
        } catch (final RuntimeException e) {
            // Get the before version.
            entityEventLog.update(before, node, e);
            throw e;
        }

        return result;
    }
}
