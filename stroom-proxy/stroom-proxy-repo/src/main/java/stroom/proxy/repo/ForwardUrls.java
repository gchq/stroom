package stroom.proxy.repo;

import stroom.proxy.repo.dao.ForwardUrlDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ForwardUrls {

    private final ForwardUrlDao forwardUrlDao;
    private final ForwarderDestinations forwarderDestinations;

    private final List<ForwardUrl> forwardUrls = new ArrayList<>();
    private final List<ForwardUrl> newForwardUrls = new ArrayList<>();
    private final List<ForwardUrl> oldForwardUrls = new ArrayList<>();

    @Inject
    public ForwardUrls(final ForwardUrlDao forwardUrlDao,
                       final ForwarderDestinations forwarderDestinations) {
        this.forwardUrlDao = forwardUrlDao;
        this.forwarderDestinations = forwarderDestinations;

        // Validate.
        if (forwarderDestinations.getDestinationNames() == null ||
                forwarderDestinations.getDestinationNames().size() == 0) {
            throw new RuntimeException("No forwarding destinations have been provided");
        }

        init();
    }

    private void init() {
        final Map<String, ForwardUrl> allForwardUrls = forwardUrlDao.getAllForwardUrls()
                .stream()
                .collect(Collectors.toMap(ForwardUrl::getUrl, Function.identity()));

        // Create a map of forward URLs to DB ids.
        for (final String destinationName : forwarderDestinations.getDestinationNames()) {
            final int id = forwardUrlDao.getForwardUrlId(destinationName);
            final ForwardUrl forwardUrl = new ForwardUrl(id, destinationName);
            forwardUrls.add(forwardUrl);

            final ForwardUrl existing = allForwardUrls.remove(destinationName);
            if (existing == null) {
                newForwardUrls.add(forwardUrl);
            }
        }

        // Any remaining URLs are old.
        oldForwardUrls.addAll(allForwardUrls.values());
    }

    public List<ForwardUrl> getForwardUrls() {
        return forwardUrls;
    }

    public List<ForwardUrl> getNewForwardUrls() {
        return newForwardUrls;
    }

    public List<ForwardUrl> getOldForwardUrls() {
        return oldForwardUrls;
    }
}
