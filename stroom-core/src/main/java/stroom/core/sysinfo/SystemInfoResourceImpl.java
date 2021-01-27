package stroom.core.sysinfo;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import event.logging.Resource;
import event.logging.ViewEventAction;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.stream.Collectors;

public class SystemInfoResourceImpl implements SystemInfoResource {

    private final SystemInfoService systemInfoService;
    private final StroomEventLoggingService stroomEventLoggingService;

    @Inject
    public SystemInfoResourceImpl(final SystemInfoService systemInfoService,
                                  final StroomEventLoggingService stroomEventLoggingService) {
        this.systemInfoService = systemInfoService;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    @Override
    public SystemInfoResultList getAll() {

        return stroomEventLoggingService.loggedResult(
                "getAllSystemInfo",
                "Getting all system info results",
                buildViewEventAction(""),
                () ->
                        SystemInfoResultList.of(systemInfoService.getAll())
        );
    }

    @Override
    public List<String> getNames() {
        return stroomEventLoggingService.loggedResult(
                "getAllSystemInfo",
                "Getting all system info result names",
                buildViewEventAction(NAMES_PATH_PART),
                () -> systemInfoService.getNames().stream()
                        .sorted()
                        .collect(Collectors.toList())
        );
    }

    @Override
    public SystemInfoResult get(final String name) {

        if (name == null || name.isEmpty()) {
            throw new BadRequestException("name not supplied");
        }

        return stroomEventLoggingService.loggedResult(
                "getSystemInfo",
                "Getting system info results for " + name,
                buildViewEventAction("/"),
                () -> systemInfoService.get(name)
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
