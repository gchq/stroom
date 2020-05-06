package stroom.core.sysinfo;

import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.PermissionException;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class SystemInfoServiceImpl implements SystemInfoService {

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
        return systemInfoSuppliers.values().stream()
                .map(HasSystemInfo::getSystemInfo)
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> getNames() {
        checkPermission();
        return systemInfoSuppliers.keySet();
    }

    @Override
    public Optional<SystemInfoResult> get(final String name) {
        checkPermission();

        // We should have a user in context as this is coming from an authenticated rest api
        final HasSystemInfo systemInfoSupplier = systemInfoSuppliers.get(name);

        return Optional.ofNullable(systemInfoSupplier)
                .map(HasSystemInfo::getSystemInfo);
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.VIEW_SYSTEM_INFO_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to view system information"
            );
        }
    }
}
