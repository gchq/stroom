package stroom.appstore.impl;

import stroom.appstore.api.AppStoreConfig;
import stroom.appstore.shared.AppStoreResponse;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.gitrepo.api.GitRepoStore;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.shared.DocPath;
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
import java.util.Collections;
import java.util.List;

/**
 * REST server-side implementation for the AppStore stuff.
 */
@AutoLogged
public class AppStoreResourceImpl implements AppStoreResource {

    /** Where we get configuration from */
    private final AppStoreConfig config;

    /** The store used to create a GitRepo */
    private final GitRepoStore gitRepoStore;

    private final ExplorerService explorerService;

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
    public AppStoreResourceImpl(final AppStoreConfig config,
                                GitRepoStore gitRepoStore,
                                ExplorerService explorerService,
                                ExplorerNodeService explorerNodeService) {
        this.config = config;
        this.gitRepoStore = gitRepoStore;
        this.explorerService = explorerService;
    }

    /**
     * REST method to return the list of content packs to the client.
     * <br/>
     * Gets called repeatedly by the client, every minute or so.
     *
     * @return A list of content packs. Never returns null but may
     * return an empty list.
     */
    @SuppressWarnings("unused")
    @Override
    public ResultPage<AppStoreContentPack> list(PageRequest pageRequest) {

        ObjectMapper mapper = YamlUtil.getMapper();

        List<String> appStoreUrls = config.getAppStoreUrls();
        List<AppStoreContentPack> contentPacks;

        for (String appStoreUrl : appStoreUrls) {
            LOGGER.info("Parsing appStore at '{}'", appStoreUrl);

            try {
                URI uri = new URI(appStoreUrl);
                InputStream istr = new BufferedInputStream(uri.toURL().openStream());
                ContentPacks cps = mapper.readValue(istr, ContentPacks.class);
                LOGGER.info("Adding content packs from '{}' -> '{}'", appStoreUrl, cps);
                contentPacks = cps.getContentPacks();

                // Resolve the SVG icons into the Content Pack
                // and set the content store meta-data
                for (var cp : contentPacks) {
                    this.resolveSvgIcon(cp);
                    cp.setContentStoreUiName(cps.getUiName());
                }
                return ResultPage.createPageLimitedList(contentPacks, pageRequest);
            } catch (URISyntaxException | MalformedURLException e) {
                LOGGER.error("Cannot parse App Store URL '{}'.", appStoreUrl, e);
            } catch (UnrecognizedPropertyException e) {
                LOGGER.error("Cannot parse App Store URL '{}': {}", appStoreUrl, e.getMessage(), e);
            } catch (IOException e) {
                LOGGER.error("Cannot connect to App Store URL '{}'.", appStoreUrl, e);
            }
        }

        // Get here and something has gone wrong so return empty list.
        return ResultPage.createPageLimitedList(Collections.emptyList(), pageRequest);
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

    /**
     * Checks to see if a GitRepo exists that matches the content pack.
     * Not a full check to see if there is a match; just compare
     * Git URL, branch and path to try to avoid duplicate objects.
     * @param contentPack The content pack to check for a match.
     * @return true if the GitRepo already exists; false if not.
     */
    @Override
    public boolean exists(AppStoreContentPack contentPack) {
        List<DocRef> existingDocRefs = gitRepoStore.list();
        for (var existingDocRef : existingDocRefs) {
            GitRepoDoc existingGitRepoDoc = gitRepoStore.readDocument(existingDocRef);
            if (contentPack.matches(existingGitRepoDoc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a GitRepoDoc from a Content Pack.
     * @param contentPack The content pack that holds the data for the GitRepoDoc.
     */
    @Override
    public AppStoreResponse create(AppStoreContentPack contentPack) {
        final AppStoreResponse response;

        if (this.exists(contentPack)) {
            LOGGER.error("Content pack already exists within Stroom");
            response = new AppStoreResponse(false, "Content pack already exists");
        } else {
            try {
                // Put the document into the Explorer Tree
                LOGGER.info("Creating DocPath from '{}'", contentPack.getStroomPath());
                DocPath docPathToGitRepo = DocPath.fromPathString(contentPack.getStroomPath());
                ExplorerNode parentNode = explorerService.ensureFolderPath(docPathToGitRepo,
                        PermissionInheritance.DESTINATION);
                ExplorerNode gitRepoNode = explorerService.create(
                        GitRepoDoc.TYPE,
                        contentPack.getUiName(),
                        parentNode,
                        PermissionInheritance.DESTINATION);

                // Update the GitRepoDoc
                DocRef docRef = gitRepoNode.getDocRef();
                GitRepoDoc gitRepoDoc = gitRepoStore.readDocument(docRef);
                contentPack.updateSettingsIn(gitRepoDoc);
                gitRepoStore.writeDocument(gitRepoDoc);

                // TODO Refresh Explorer Tree

                // TODO Pull if necessary

                // Tell the user it worked
                response = new AppStoreResponse(true,
                        "Created '" + contentPack.getUiName() + "'");
            } catch (RuntimeException e) {
                LOGGER.error("Error creating GitRepo: {}", e.getMessage(), e);
                throw e;
            }
        }

        return response;
    }

}
