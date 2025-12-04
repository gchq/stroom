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

package stroom.event.logging.api;

import event.logging.BaseObject;

/**
 * An {@link ObjectInfoProvider} that does the default automatic conversion of the object
 * to a {@link BaseObject} but allows for the setting of a custom object type.
 */
public abstract class AbstractAutoObjectInfoProvider implements ObjectInfoProvider {

    private final StroomEventLoggingService stroomEventLoggingService;

    public AbstractAutoObjectInfoProvider(final StroomEventLoggingService stroomEventLoggingService) {
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    @Override
    public BaseObject createBaseObject(final Object object) {
        // False to stop the infinite loop and stack overflow
        return stroomEventLoggingService.convert(object, false);
    }

    public abstract String getObjectType(final Object object);
}
