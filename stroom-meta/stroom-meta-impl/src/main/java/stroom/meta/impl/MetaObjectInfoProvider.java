/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.meta.impl;

import event.logging.BaseObject;
import event.logging.Object;
import event.logging.util.EventLoggingUtil;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.meta.shared.Meta;

class MetaObjectInfoProvider implements ObjectInfoProvider {
    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final Meta meta = (Meta) obj;

        final Object object = new Object();
        object.setType("Data");
        object.setId(String.valueOf(meta.getId()));
        if (meta.getFeedName() != null) {
            EventLoggingUtil.createData("Feed", meta.getFeedName());
        }
        if (meta.getTypeName() != null) {
            object.getData().add(EventLoggingUtil.createData("Type", meta.getTypeName()));
        }
        return object;
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
