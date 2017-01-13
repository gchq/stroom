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

package stroom.entity.shared;

import stroom.util.shared.SharedObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This has been put here as import export API is not Serializable.
 */
public class EntityActionConfirmation implements SharedObject {
    private static final long serialVersionUID = -5451928033766595954L;

    private String path;
    private String entityType;
    private boolean action;
    private boolean warning = false;
    private List<String> messageList = new ArrayList<String>();
    private List<String> updatedFieldList = new ArrayList<String>();
    private EntityAction entityAction;

    public static Map<String, EntityActionConfirmation> asMap(List<EntityActionConfirmation> list) {
        Map<String, EntityActionConfirmation> map = new HashMap<String, EntityActionConfirmation>();
        for (EntityActionConfirmation e : list) {
            map.put(e.getPath(), e);
        }
        return map;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String type) {
        this.entityType = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public EntityAction getEntityAction() {
        return entityAction;
    }

    public void setEntityAction(EntityAction entityAction) {
        this.entityAction = entityAction;
    }

    public boolean isWarning() {
        return warning;
    }

    public void setWarning(boolean warning) {
        this.warning = warning;
    }

    public List<String> getMessageList() {
        return messageList;
    }

    public void setMessageList(List<String> messageList) {
        this.messageList = messageList;
    }

    public boolean isAction() {
        return action;
    }

    public void setAction(boolean action) {
        this.action = action;
    }

    public List<String> getUpdatedFieldList() {
        return updatedFieldList;
    }

    public void setUpdatedFieldList(List<String> updatedFieldList) {
        this.updatedFieldList = updatedFieldList;
    }

    @Override
    public String toString() {
        return path + " " + entityType + " " + entityAction + " " + action;
    }
}
