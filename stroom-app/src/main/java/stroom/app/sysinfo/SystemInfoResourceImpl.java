package stroom.app.sysinfo;

import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import event.logging.Event;
import event.logging.EventLoggingService;
import event.logging.ObjectOutcome;
import event.logging.Resource;
import event.logging.util.EventLoggingUtil;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;

public class SystemInfoResourceImpl implements SystemInfoResource {

    private final SystemInfoService systemInfoService;
    private final EventLoggingService eventLoggingService;

    @Inject
    public SystemInfoResourceImpl(final SystemInfoService systemInfoService,
                                  final EventLoggingService eventLoggingService) {
        this.systemInfoService = systemInfoService;
        this.eventLoggingService = eventLoggingService;
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
    public SystemInfoResult get(final String name) {

        if (name == null || name.isEmpty()) {
            throw new BadRequestException("name not supplied");
        }

        logViewResourceEvent(
                "getSystemInfo",
                "Getting system info results for " + name,
                "/" + name);

        final SystemInfoResult systemInfoResult = systemInfoService.get(name);

        if (systemInfoResult == null) {
            throw new NotFoundException(LogUtil.message("Name {} not found", name));
        }
        return systemInfoResult;
    }

    private void logViewResourceEvent(final String typeId,
                                      final String description,
                                      final String subPath) {

        final Event event = eventLoggingService.createEvent();
        final Event.EventDetail eventDetail = EventLoggingUtil.createEventDetail(typeId, description);
        final Resource resource = new Resource();
        resource.setURL(ResourcePaths.buildAuthenticatedApiPath(SystemInfoResource.BASE_PATH, subPath));
        final ObjectOutcome objectOutcome = new ObjectOutcome();
        objectOutcome.getObjects().add(resource);
        eventDetail.setView(objectOutcome);
        event.setEventDetail(eventDetail);
        eventLoggingService.log(event);
    }
}
