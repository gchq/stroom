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

import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.HasSystemInfo.ParamInfo;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// If we make it a singleton due to systemInfoSuppliers then we would
// probably need the injected set to be a map of providers instead.
public class SystemInfoServiceImpl implements SystemInfoService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SystemInfoServiceImpl.class);

    private final Map<String, HasSystemInfo> systemInfoSuppliers;
    private final SecurityContext securityContext;

    @Inject
    public SystemInfoServiceImpl(final Set<HasSystemInfo> systemInfoSuppliers,
                                 final SecurityContext securityContext) {
        this.systemInfoSuppliers = systemInfoSuppliers.stream()
                .collect(Collectors.toMap(HasSystemInfo::getSystemInfoName, Function.identity()));
        this.securityContext = securityContext;
    }

    @Override
    public List<SystemInfoResult> getAll() {
        checkPermission();
        // We should have a user in context as this is coming from an authenticated rest api
        return systemInfoSuppliers.values()
                .stream()
                .map(HasSystemInfo::getSystemInfo)
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> getNames() {
        checkPermission();
        return systemInfoSuppliers.keySet();
    }

    @Override
    public List<ParamInfo> getParamInfo(final String providerName) {
        checkPermission();
        final HasSystemInfo systemInfoSupplier = Objects.requireNonNull(
                systemInfoSuppliers.get(providerName),
                () -> LogUtil.message("Unknown system info provider name [{}]", providerName));
        return systemInfoSupplier.getParamInfo();
    }

    @Override
    public Optional<SystemInfoResult> get(final String providerName) {
        return get(providerName, Collections.emptyMap());
    }

    @Override
    public Optional<SystemInfoResult> get(final String providerName, final Map<String, String> params) {
        checkPermission();

        // We should have a user in context as this is coming from an authenticated rest api
        final HasSystemInfo systemInfoSupplier = systemInfoSuppliers.get(providerName);

        try {
            return NullSafe.getAsOptional(
                    systemInfoSupplier,
                    supplier -> supplier.getSystemInfo(params));
        } catch (final Exception e) {
            LOGGER.error("Error getting system info for {} with params {}: {}",
                    providerName, params, e.getMessage(), e);
            throw e;
        }
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(AppPermission.VIEW_SYSTEM_INFO_PERMISSION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view system information"
            );
        }
    }
}
