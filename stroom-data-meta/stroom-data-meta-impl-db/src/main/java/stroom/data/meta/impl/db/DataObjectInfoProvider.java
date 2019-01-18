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

package stroom.data.meta.impl.db;

import event.logging.BaseObject;
import event.logging.Object;
import event.logging.util.EventLoggingUtil;
import stroom.data.meta.api.Data;
import stroom.event.logging.api.ObjectInfoProvider;

class DataObjectInfoProvider implements ObjectInfoProvider {
    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final Data stream = (Data) obj;

        final Object object = new Object();
        object.setType("Stream");
        object.setId(String.valueOf(stream.getId()));
        if (stream.getFeedName() != null) {
            EventLoggingUtil.createData("Feed", stream.getFeedName());
        }
        if (stream.getTypeName() != null) {
            object.getData().add(EventLoggingUtil.createData("StreamType", stream.getTypeName()));
        }
        return object;
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
