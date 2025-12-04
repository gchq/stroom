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

package stroom.security.impl;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import event.logging.BaseObject;
import event.logging.Data;
import event.logging.User.Builder;

public class UserObjectInfoProvider implements ObjectInfoProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final Object object) {
        if (object != null) {
            final User user = (User) object;

            final Builder<Void> builder = event.logging.User.builder()
                    .withId(user.getSubjectId())
                    .withName(user.getDisplayName())
                    .addData(Data.builder()
                            .withName("type")
                            .withValue(user.getType())
                            .build());

            if (user.getSubjectId() == null
                && user.getDisplayName() == null
                && user.getUuid() != null) {
                builder.addData(Data.builder()
                        .withName("uuid")
                        .withValue(user.getUuid())
                        .build());
            }

            if (NullSafe.contains(user.getDisplayName(), "@")) {
                builder.withEmailAddress(user.getDisplayName());
            }
            return builder.build();
        } else {
            return null;
        }
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
