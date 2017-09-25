package stroom.node.server;

import org.springframework.remoting.caucho.HessianServiceExporter;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@Component("remoteStatusServiceRPC")
class RemoteStatusServiceRPC extends HessianServiceExporter {
    @Inject
    RemoteStatusServiceRPC(@Named("remoteStatusService") final RemoteStatusService remoteStatusService) {
        setService(remoteStatusService);
        setServiceInterface(RemoteStatusService.class);
    }
}
