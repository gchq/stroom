package stroom.core.sysinfo;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import event.logging.Event;
import event.logging.ObjectOutcome;
import event.logging.Resource;
import event.logging.util.EventLoggingUtil;

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

        logViewResourceEvent(
                "getAllSystemInfo",
                "Getting all system info results",
                "");

        return SystemInfoResultList.of(systemInfoService.getAll());
    }

    @Override
    public List<String> getNames() {
        logViewResourceEvent(
                "getAllSystemInfo",
                "Getting all system info result names",
                NAMES_PATH_PART);

        return systemInfoService.getNames().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public SystemInfoResult get(final String name) {

        if (name == null || name.isEmpty()) {
            throw new BadRequestException("name not supplied");
        }

        logViewResourceEvent(
                "getSystemInfo",
                "Getting system info results for " + name,
                "/" + name);

        return systemInfoService.get(name)
                .orElseThrow(() ->
                        new NotFoundException(LogUtil.message("Name {} not found", name)));
    }

    private void logViewResourceEvent(final String typeId,
                                      final String description,
                                      final String subPath) {

        final Event event = stroomEventLoggingService.createEvent();
        final Event.EventDetail eventDetail = EventLoggingUtil.createEventDetail(typeId, description);
        final Resource resource = new Resource();
        resource.setURL(ResourcePaths.buildAuthenticatedApiPath(SystemInfoResource.BASE_PATH, subPath));
        final ObjectOutcome objectOutcome = new ObjectOutcome();
        objectOutcome.getObjects().add(resource);
        eventDetail.setView(objectOutcome);
        event.setEventDetail(eventDetail);
        stroomEventLoggingService.log(event);
    }
}
