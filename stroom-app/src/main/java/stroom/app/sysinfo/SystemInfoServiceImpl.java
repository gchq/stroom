package stroom.app.sysinfo;

import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SystemInfoServiceImpl implements SystemInfoService {

    private final Map<String, HasSystemInfo> systemInfoSuppliers;

    @Inject
    public SystemInfoServiceImpl(final Set<HasSystemInfo> systemInfoSuppliers) {
        this.systemInfoSuppliers = systemInfoSuppliers.stream()
                .collect(Collectors.toMap(HasSystemInfo::getName, Function.identity()));
    }

    @Override
    public List<SystemInfoResult> getAll() {
        // We should have a user in context as this is coming from an authenticated rest api
        return systemInfoSuppliers.values().stream()
                .map(HasSystemInfo::getSystemInfo)
                .collect(Collectors.toList());
    }

    @Override
    public SystemInfoResult get(final String name) {
        // We should have a user in context as this is coming from an authenticated rest api
        final HasSystemInfo systemInfoSupplier = systemInfoSuppliers.get(name);


        return systemInfoSupplier.getSystemInfo();
    }
}
