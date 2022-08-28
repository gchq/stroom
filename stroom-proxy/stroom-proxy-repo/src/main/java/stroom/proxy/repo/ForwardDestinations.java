package stroom.proxy.repo;

import stroom.proxy.repo.dao.ForwardDestDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ForwardDestinations {

    private final ForwardDestDao forwardDestDao;
    private final ForwarderDestinations forwarderDestinations;

    private final List<ForwardDest> forwardDests = new ArrayList<>();
    private final List<ForwardDest> newForwardDests = new ArrayList<>();
    private final List<ForwardDest> oldForwardDests = new ArrayList<>();

    @Inject
    public ForwardDestinations(final ForwardDestDao forwardDestDao,
                               final ForwarderDestinations forwarderDestinations) {
        this.forwardDestDao = forwardDestDao;
        this.forwarderDestinations = forwarderDestinations;

        // Validate.
        if (forwarderDestinations.getDestinationNames() == null ||
                forwarderDestinations.getDestinationNames().size() == 0) {
            throw new RuntimeException("No forwarding destinations have been provided");
        }

        init();
    }

    private void init() {
        final Map<String, ForwardDest> allForwardDests = forwardDestDao.getAllForwardDests()
                .stream()
                .collect(Collectors.toMap(ForwardDest::getName, Function.identity()));

        // Create a map of forward URLs to DB ids.
        for (final String destinationName : forwarderDestinations.getDestinationNames()) {
            final int id = forwardDestDao.getForwardDestId(destinationName);
            final ForwardDest forwardDest = new ForwardDest(id, destinationName);
            forwardDests.add(forwardDest);

            final ForwardDest existing = allForwardDests.remove(destinationName);
            if (existing == null) {
                newForwardDests.add(forwardDest);
            }
        }

        // Any remaining URLs are old.
        oldForwardDests.addAll(allForwardDests.values());
    }

    public List<ForwardDest> getForwardDests() {
        return forwardDests;
    }

    public List<ForwardDest> getNewForwardDests() {
        return newForwardDests;
    }

    public List<ForwardDest> getOldForwardDests() {
        return oldForwardDests;
    }
}
