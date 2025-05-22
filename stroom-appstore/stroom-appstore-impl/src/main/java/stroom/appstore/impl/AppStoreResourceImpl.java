package stroom.appstore.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.appstore.shared.AppStoreContentPack;
import stroom.appstore.shared.AppStoreResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * REST server-side implementation for the AppStore stuff.
 */
@AutoLogged
public class AppStoreResourceImpl implements AppStoreResource {

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(AppStoreResourceImpl.class);

    /**
     * REST method to return the list of content packs to the client.
     * @return A list of content packs. Never returns null but may
     * return an empty list.
     */
    @SuppressWarnings("unused")
    @Override
    public ResultPage<AppStoreContentPack> list(PageRequest pageRequest) {

        // TODO proper implementation later
        ArrayList<AppStoreContentPack> contentPacks = new ArrayList<>();
        contentPacks.add(new AppStoreContentPack(
                "Core XML Schemas",
                null,
                null,
                null,
                "https://github.com/gchq/stroom-content.git",
                "planb",
                "source/core-xml-schemas/stroomContent",
                "e4081362f006bed7f14f376a2049ccbcb56b1144"
                ));
        LOGGER.error("{}", contentPacks);
        return ResultPage.createPageLimitedList(contentPacks, pageRequest);
    }
}
