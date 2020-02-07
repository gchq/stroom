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

package stroom.config.global.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyResultPage;
import stroom.config.global.shared.ConfigResource;
import stroom.config.global.shared.FindGlobalConfigCriteria;
import stroom.node.api.NodeInfo;
import stroom.ui.config.shared.UiConfig;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Optional;

class ConfigResourceImpl implements ConfigResource, RestResource, HasHealthCheck {
    private final GlobalConfigService globalConfigService;
    private final UiConfig uiConfig;

    @Inject
    ConfigResourceImpl(final GlobalConfigService globalConfigService,
                       final UiConfig uiConfig,
                       final Provider<BuildInfo> buildInfoProvider,
                       final NodeInfo nodeInfo) {
        this.globalConfigService = globalConfigService;
        this.uiConfig = uiConfig;
        uiConfig.setBuildInfo(buildInfoProvider.get());
        uiConfig.setNodeName(nodeInfo.getThisNodeName());
    }

    @Override
    public List<ConfigProperty> getAllConfig() {
        try {
            return globalConfigService.list();
        } catch (final RuntimeException e) {
            throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public ConfigProperty getPropertyByName(final String propertyName) {
        try {
            final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(propertyName);
            return optConfigProperty.orElseThrow(NotFoundException::new);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public String getYamlValueByName(final String propertyName) {
        try {
            final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(propertyName);
            return optConfigProperty
                    .flatMap(configProperty ->
                            configProperty.getYamlOverrideValue().getVal())
                    .orElseThrow(NotFoundException::new);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public ConfigPropertyResultPage find(final FindGlobalConfigCriteria criteria) {
        return new ConfigPropertyResultPage().limited(globalConfigService.list(criteria), criteria.obtainPageRequest());
    }

    @Override
    public ConfigProperty read(final Integer id) {
        return globalConfigService.fetch(id).orElseThrow(() ->
                new RuntimeException(LogUtil.message("No config property found with ID [{}]", id)));
    }

    @Override
    public ConfigProperty update(final ConfigProperty doc) {
        return globalConfigService.update(doc);
    }

    @Override
    public UiConfig fetchUiConfig() {
        return uiConfig;
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}