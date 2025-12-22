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

package stroom.security.impl.apikey;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.security.shared.HashedApiKey;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import event.logging.BaseObject;
import event.logging.OtherObject;
import event.logging.OtherObject.Builder;
import event.logging.util.DateUtil;
import event.logging.util.EventLoggingUtil;

public class ApiKeyObjectInfoProvider implements ObjectInfoProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final Object object) {
        final HashedApiKey apiKey = (HashedApiKey) object;

        final Builder<Void> builder = OtherObject.builder()
                .withType("ApiKey")
                .withId(String.valueOf(apiKey.getId()))
                .withName(apiKey.getName())
                .withDescription(apiKey.getComments())
                .withState(apiKey.getEnabled()
                        ? "Enabled"
                        : "Disabled");

        try {
            builder
                    .addData(EventLoggingUtil.createData("Owner",
                            NullSafe.get(apiKey.getOwner(), UserRef::toInfoString)))
                    .addData(EventLoggingUtil.createData("Expiry",
                            DateUtil.createNormalDateTimeString(apiKey.getExpireTimeMs())))
                    .addData(EventLoggingUtil.createData("Prefix", apiKey.getApiKeyPrefix()));
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to add unknown but useful data!", e);
        }

        return builder.build();
    }

    @Override
    public String getObjectType(final Object object) {
        return "API Key";
    }
}
