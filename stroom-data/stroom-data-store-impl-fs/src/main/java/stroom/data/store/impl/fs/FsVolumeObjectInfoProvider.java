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

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.event.logging.api.ObjectInfoProvider;

import event.logging.BaseObject;
import event.logging.Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FsVolumeObjectInfoProvider implements ObjectInfoProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FsVolumeObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final FsVolume fsVolume = (FsVolume) obj;

        final Object object = new Object();
        object.setType("Data Volume");
        object.setId(String.valueOf(fsVolume.getId()));
        object.setName(fsVolume.getPath());
        object.setState(fsVolume.getStatus().getDisplayValue());

        return object;
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
