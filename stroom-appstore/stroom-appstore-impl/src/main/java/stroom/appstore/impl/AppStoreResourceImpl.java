package stroom.appstore.impl;

import stroom.appstore.api.AppStoreConfig;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.appstore.shared.AppStoreContentPack;
import stroom.appstore.shared.AppStoreResource;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST server-side implementation for the AppStore stuff.
 */
@AutoLogged
public class AppStoreResourceImpl implements AppStoreResource {

    /** Where we get configuration from */
    private final AppStoreConfig config;

    /** The size of the buffer used to copy stuff around */
    private static final int IO_BUF_SIZE = 4096;

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(AppStoreResourceImpl.class);

    /**
     * Injected constructor.
     * @param config Where to get configuration data from.
     */
    @SuppressWarnings("unused")
    @Inject
    public AppStoreResourceImpl(final AppStoreConfig config) {
        this.config = config;
    }

    /**
     * REST method to return the list of content packs to the client.
     * @return A list of content packs. Never returns null but may
     * return an empty list.
     */
    @SuppressWarnings("unused")
    @Override
    public ResultPage<AppStoreContentPack> list(PageRequest pageRequest) {

        ObjectMapper mapper = YamlUtil.getMapper();

        List<String> appStoreUrls = config.getAppStoreUrls();
        Map<String, AppStoreContentPack> contentPacks = new HashMap<>();

        for (String appStoreUrl : appStoreUrls) {
            LOGGER.info("Parsing appStore at '{}'", appStoreUrl);

            try {
                URI uri = new URI(appStoreUrl);
                InputStream istr = new BufferedInputStream(uri.toURL().openStream());
                ContentPacks cps = mapper.readValue(istr, ContentPacks.class);
                contentPacks.putAll(cps.getMap());
                LOGGER.info("Adding content packs from '{}' -> '{}'", appStoreUrl, cps);

            } catch (URISyntaxException | MalformedURLException e) {
                LOGGER.error("Cannot parse App Store URL '{}'.", appStoreUrl, e);
            } catch (UnrecognizedPropertyException e) {
                LOGGER.error("Cannot parse App Store URL '{}': {}", appStoreUrl, e.getMessage(), e);
            } catch (IOException e) {
                LOGGER.error("Cannot connect to App Store URL '{}'.", appStoreUrl, e);
            }
        }

        // Resolve the SVG icons into the Content Pack
        for (var cp : contentPacks.values()) {
            this.resolveSvgIcon(cp);
        }

        // For now return the result as a list for back compatibility
        List<AppStoreContentPack> listOfContentPacks = new ArrayList<>(contentPacks.values());
        return ResultPage.createPageLimitedList(listOfContentPacks, pageRequest);
    }

    /**
     * Looks at the content pack. If there isn't an SVG icon set and
     * there is a URL for the icon, then download that icon and put it
     * into the content pack.
     * If something goes wrong then log an error.
     *
     * @param contentPack The thing to update with an icon if necessary
     *                    and available.
     */
    private void resolveSvgIcon(AppStoreContentPack contentPack) {
        if (contentPack.getIconSvg() == null) {
            try {
                byte[] buffer = new byte[IO_BUF_SIZE];
                URI uri = new URI(contentPack.getIconUrl());
                InputStream istr = new BufferedInputStream(uri.toURL().openStream());
                ByteArrayOutputStream ostr = new ByteArrayOutputStream();

                for (int length; (length = istr.read(buffer)) != -1; ) {
                    ostr.write(buffer, 0, length);
                }

                contentPack.setIconSvg(ostr.toString(StandardCharsets.UTF_8));

            } catch (URISyntaxException e) {
                LOGGER.error("Cannot parse the icon URL for content pack '{}': {}",
                        contentPack.getUiName(),
                        e.getMessage(),
                        e);
            } catch (IOException e) {
                LOGGER.error("Error downloading icon for content pack '{}' from '{}': {}",
                        contentPack.getUiName(),
                        contentPack.getIconUrl(),
                        e.getMessage(),
                        e);
            }
        }
    }

}
