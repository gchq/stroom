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

package stroom.core.sysinfo;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.sysinfo.HasSystemInfo.ParamInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import event.logging.Resource;
import event.logging.ViewEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@AutoLogged(OperationType.MANUALLY_LOGGED)
public class SystemInfoResourceImpl implements SystemInfoResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemInfoResourceImpl.class);

    private final Provider<SystemInfoService> systemInfoServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    public SystemInfoResourceImpl(final Provider<SystemInfoService> systemInfoServiceProvider,
                                  final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.systemInfoServiceProvider = systemInfoServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Override
    public SystemInfoResultList getAll() {

        return stroomEventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId("getAllSystemInfo")
                .withDescription("Getting all system info results")
                .withDefaultEventAction(buildViewEventAction(""))
                .withSimpleLoggedResult(() ->
                        SystemInfoResultList.of(systemInfoServiceProvider.get().getAll()))
                .getResultAndLog();
    }

    @Override
    public List<String> getNames() {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId("getAllSystemInfo")
                .withDescription("Getting all system info result names")
                .withDefaultEventAction(buildViewEventAction(NAMES_PATH_PART))
                .withSimpleLoggedResult(() -> systemInfoServiceProvider.get().getNames().stream()
                        .sorted()
                        .collect(Collectors.toList()))
                .getResultAndLog();
    }

    @Override
    public List<ParamInfo> getParams(final String name) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId("getAllSystemInfo")
                .withDescription("Getting all system info result names")
                .withDefaultEventAction(buildViewEventAction(NAMES_PATH_PART))
                .withSimpleLoggedResult(() -> systemInfoServiceProvider.get().getParamInfo(name))
                .getResultAndLog();
    }

    @Override
    public SystemInfoResult get(final UriInfo uriInfo,
                                final String providerName) {

        if (providerName == null || providerName.isEmpty()) {
            throw RestUtil.badRequest("name not supplied");
        }

        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId("getSystemInfo")
                .withDescription("Getting system info results for " + providerName)
                .withDefaultEventAction(buildViewEventAction("/"))
                .withSimpleLoggedResult(() -> {
                    try {
                        final Map<String, String> queryParams = getQueryParams(uriInfo);
                        validateParams(queryParams, providerName);

                        LOGGER.debug("Params: [{}]",
                                queryParams.entrySet()
                                        .stream()
                                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                                        .collect(Collectors.joining(", ")));

                        return systemInfoServiceProvider.get().get(providerName, queryParams)
                                .orElseThrow(() ->
                                        new NotFoundException(LogUtil.message("Name {} not found", providerName)));
                    } catch (final Exception e) {
                        LOGGER.error(LogUtil.message("Error getting system info for {}. {}",
                                providerName, e.getMessage()), e);
                        throw e;
                    }
                })
                .getResultAndLog();
    }

    private void validateParams(final Map<String, String> params,
                                final String providerName) {
        final List<ParamInfo> paramInfo = systemInfoServiceProvider.get().getParamInfo(providerName);

        final String missingMandatoryParams = paramInfo.stream()
                .filter(ParamInfo::isMandatory)
                .map(ParamInfo::getName)
                .filter(paramName -> !params.containsKey(paramName))
                .collect(Collectors.joining(", "));

        if (!missingMandatoryParams.isBlank()) {
            throw new RuntimeException(LogUtil.message(
                    "The following query parameter(s) must have a value [{}]",
                    missingMandatoryParams));
        }
    }

    private Map<String, String> getQueryParams(final UriInfo uriInfo) {
        final MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        // TODO For now just take the first value for each key.
        //  In future we may want to pass down the MultivaluedMap or a Guava MultiMap
        return queryParameters.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry -> NullSafe.get(entry.getValue(), list -> list.get(0))));
    }

    private ViewEventAction buildViewEventAction(final String subPath) {

        return ViewEventAction.builder()
                .addResource(Resource.builder()
                        .withURL(ResourcePaths.buildAuthenticatedApiPath(SystemInfoResource.BASE_PATH, subPath))
                        .build())
                .build();
    }
}
