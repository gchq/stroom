package stroom.core.sysinfo;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import event.logging.Resource;
import event.logging.ViewEventAction;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;

@AutoLogged(OperationType.MANUALLY_LOGGED)
public class SystemInfoResourceImpl implements SystemInfoResource {

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
                .loggedResult(
                "getAllSystemInfo",
                "Getting all system info results",
                buildViewEventAction(""),
                () ->
                        SystemInfoResultList.of(systemInfoServiceProvider.get().getAll())
        );
    }

    @Override
    public List<String> getNames() {
        return stroomEventLoggingServiceProvider.get().loggedResult(
                "getAllSystemInfo",
                "Getting all system info result names",
                buildViewEventAction(NAMES_PATH_PART),
                () -> systemInfoServiceProvider.get().getNames().stream()
                        .sorted()
                        .collect(Collectors.toList())
        );
    }

    @Override
    public SystemInfoResult get(final String name) {

        if (name == null || name.isEmpty()) {
            throw new BadRequestException("name not supplied");
        }

        return stroomEventLoggingServiceProvider.get().loggedResult(
                "getSystemInfo",
                "Getting system info results for " + name,
                buildViewEventAction("/"),
                () -> systemInfoServiceProvider.get().get(name)
                        .orElseThrow(() ->
                                new NotFoundException(LogUtil.message("Name {} not found", name)))
        );
    }

    private ViewEventAction buildViewEventAction(final String subPath) {

        return ViewEventAction.builder()
                .addResource(Resource.builder()
                        .withURL(ResourcePaths.buildAuthenticatedApiPath(SystemInfoResource.BASE_PATH, subPath))
                        .build())
                .build();
    }
}
