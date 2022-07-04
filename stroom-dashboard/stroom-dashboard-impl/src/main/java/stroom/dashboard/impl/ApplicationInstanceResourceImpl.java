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
 */

package stroom.dashboard.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.instance.shared.ApplicationInstanceInfo;
import stroom.instance.shared.ApplicationInstanceResource;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class ApplicationInstanceResourceImpl implements ApplicationInstanceResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApplicationInstanceResourceImpl.class);

    private final Provider<ApplicationInstanceManager> provider;

    @Inject
    ApplicationInstanceResourceImpl(final Provider<ApplicationInstanceManager> provider) {
        this.provider = provider;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ApplicationInstanceInfo register() {
        final ApplicationInstance applicationInstance = provider.get().register();
        LOGGER.trace(() -> "register() - " + applicationInstance.getUuid());
        return new ApplicationInstanceInfo(
                applicationInstance.getUuid(),
                applicationInstance.getUserId(),
                applicationInstance.getCreateTime());
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public Boolean keepAlive(final ApplicationInstanceInfo applicationInstanceInfo) {
        LOGGER.trace(() -> "keepAlive() - " + applicationInstanceInfo);
        try {
            provider.get().keepAlive(applicationInstanceInfo.getUuid());
            ThreadUtil.sleep(1000);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return false;
        }

        return true;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public Boolean destroy(final ApplicationInstanceInfo applicationInstanceInfo) {
        LOGGER.trace(() -> "remove() - " + applicationInstanceInfo);
        return provider.get().remove(applicationInstanceInfo.getUuid());
    }
}
