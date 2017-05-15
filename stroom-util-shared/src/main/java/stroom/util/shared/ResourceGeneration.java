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

package stroom.util.shared;

import java.util.List;

public class ResourceGeneration implements SharedObject {
    private static final long serialVersionUID = 8354737969578639291L;

    private ResourceKey resourceKey;
    private List<Message> messageList;

    public ResourceGeneration() {
        // Default constructor necessary for GWT serialisation.
    }

    public ResourceGeneration(ResourceKey resourceKey, List<Message> messageList) {
        this.resourceKey = resourceKey;
        this.messageList = messageList;
    }

    public List<Message> getMessageList() {
        return messageList;
    }

    public void setMessageList(List<Message> messageList) {
        this.messageList = messageList;
    }

    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(ResourceKey resourceKey) {
        this.resourceKey = resourceKey;
    }

}
