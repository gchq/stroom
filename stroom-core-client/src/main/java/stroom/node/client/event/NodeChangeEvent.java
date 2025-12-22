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

package stroom.node.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class NodeChangeEvent extends GwtEvent<NodeChangeEvent.Handler> {

    private static Type<Handler> TYPE;
    private final String nodeName;

    private NodeChangeEvent(final String nodeName) {
        this.nodeName = nodeName;
    }

    public static void fire(final HasHandlers handlers, final String nodeName) {
        handlers.fireEvent(new NodeChangeEvent(nodeName));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onChange(this);
    }

    public String getNodeName() {
        return nodeName;
    }

    @Override
    public String toString() {
        return "NodeChangeEvent{" +
                "nodeName='" + nodeName + '\'' +
                '}';
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onChange(NodeChangeEvent event);
    }
}
