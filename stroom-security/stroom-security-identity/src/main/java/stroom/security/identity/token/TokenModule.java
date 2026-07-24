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

package stroom.security.identity.token;

import stroom.job.api.ScheduledJobsBinder;
import stroom.security.openid.api.JsonWebKeyFactory;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public final class TokenModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JsonWebKeyFactory.class).to(JwkFactoryImpl.class);
        bind(PublicJsonWebKeyProvider.class).to(JwkCache.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(JwkRotation.class, jobBuilder -> jobBuilder
                        .name("Identity Key Rotation")
                        .description("Rotate the internal identity provider's token signing keys, "
                                + "retiring and eventually deleting old ones.")
                        .frequencySchedule("1d"));
    }

    private static class JwkRotation extends RunnableWrapper {

        @Inject
        JwkRotation(final JwkRotationTask jwkRotationTask) {
            super(jwkRotationTask::exec);
        }
    }
}
