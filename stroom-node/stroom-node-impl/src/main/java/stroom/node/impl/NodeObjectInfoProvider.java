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

package stroom.node.impl;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.node.shared.Node;

import event.logging.BaseObject;
import event.logging.OtherObject;
import event.logging.OtherObject.Builder;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NodeObjectInfoProvider implements ObjectInfoProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final Node node = (Node) obj;

        final Builder<Void> builder = OtherObject.builder()
                .withId(String.valueOf(node.getId()))
                .withType("Node")
                .withName(node.getName());

        try {
            builder.addData(EventLoggingUtil.createData("Url", String.valueOf(node.getUrl())));
            builder.addData(EventLoggingUtil.createData("Priority", String.valueOf(node.getPriority())));
            builder.addData(EventLoggingUtil.createData("Enabled", String.valueOf(node.isEnabled())));
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to add unknown but useful data!", e);
        }

        return builder.build();
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
