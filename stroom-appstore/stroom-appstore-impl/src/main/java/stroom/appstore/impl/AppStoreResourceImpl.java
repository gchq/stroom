package stroom.appstore.impl;

import stroom.appstore.api.AppStoreConfig;
import stroom.appstore.shared.AppStoreCreateGitRepoRequest;
import stroom.appstore.shared.AppStoreResponse;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.gitrepo.api.GitRepoStore;
import stroom.gitrepo.api.GitRepoStorageService;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.shared.DocPath;
import stroom.util.shared.Message;
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

    /** Provides access to the Explorer Tree */
    private final ExplorerService explorerService;

    /** Allows this system to automatically pull content */
    private final GitRepoStorageService gitRepoStorageService;

    /** The size of the buffer used to copy stuff around */
    private static final int IO_BUF_SIZE = 4096;

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(AppStoreResourceImpl.class);

    /**
     * Injected constructor.
     * @param config Where to get configuration data from.
     * @param gitRepoStore How to create the GitRepoDoc.
     * @param explorerService How to interact with the Explorer Tree
     * @param gitRepoStorageService How to pull content from Git
     */
    @SuppressWarnings("unused")
    @Inject
    public AppStoreResourceImpl(final AppStoreConfig config,
                                GitRepoStore gitRepoStore,
                                ExplorerService explorerService,
                                GitRepoStorageService gitRepoStorageService) {
        this.config = config;
        this.gitRepoStore = gitRepoStore;
        this.explorerService = explorerService;
        this.gitRepoStorageService = gitRepoStorageService;
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

        // Pull out the existing GitRepos so we know what exists
        List<DocRef> existingDocRefs = gitRepoStore.list();
        ArrayList<GitRepoDoc> installedGitRepoDocs = new ArrayList<>(existingDocRefs.size());
        for (var docRef : existingDocRefs) {
            // Not sure if store can return null, but handle it just in case...
            GitRepoDoc doc = gitRepoStore.readDocument(docRef);
            if (doc != null) {
                installedGitRepoDocs.add(doc);
            }
        }

        // Grab YAML describing the content store
        ObjectMapper mapper = YamlUtil.getMapper();

        List<String> appStoreUrls = config.getAppStoreUrls();
        List<AppStoreContentPack> contentPacks = new ArrayList<>();

        for (String appStoreUrl : appStoreUrls) {
            LOGGER.info("Parsing appStore at '{}'", appStoreUrl);

            try {
                URI uri = new URI(appStoreUrl);
                InputStream istr = new BufferedInputStream(uri.toURL().openStream());
                ContentStore cs = mapper.readValue(istr, ContentStore.class);

                // Fill in any extra data needed by the content packs
                List<AppStoreContentPack> listOfContentPacks = cs.getContentPacks();
                for (var cp : listOfContentPacks) {
                    // Resolve icon link to SVG text
                    this.resolveSvgIcon(cp);

                    // Add the content store metadata
                    cp.setContentStoreMetadata(cs.getMeta());

                    // Check if the content pack is already installed
                    cp.checkIfInstalled(installedGitRepoDocs);
                }

                LOGGER.info("Adding content packs from '{}' -> '{}'", appStoreUrl, cs);
                contentPacks.addAll(listOfContentPacks);

            } catch (URISyntaxException | MalformedURLException e) {
                LOGGER.error("Cannot parse App Store URL '{}'.", appStoreUrl, e);
            } catch (UnrecognizedPropertyException e) {
                LOGGER.error("Cannot parse App Store URL '{}': {}", appStoreUrl, e.getMessage(), e);
            } catch (IOException e) {
                LOGGER.error("Cannot connect to App Store URL '{}'.", appStoreUrl, e);
            }
        }


        return ResultPage.createPageLimitedList(contentPacks, pageRequest);
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
     * @param createGitRepoRequest The request holding the content pack
     *                             that holds the data for the GitRepoDoc.
     */
    @Override
    public AppStoreResponse create(AppStoreCreateGitRepoRequest createGitRepoRequest) {

        // Return value
        AppStoreResponse response;

        final List<Message> messages = new ArrayList<>();

        AppStoreContentPack contentPack = createGitRepoRequest.getContentPack();
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
                        contentPack.getGitRepoName(),
                        parentNode,
                        PermissionInheritance.DESTINATION);

                // Update the GitRepoDoc
                DocRef docRef = gitRepoNode.getDocRef();
                GitRepoDoc gitRepoDoc = gitRepoStore.readDocument(docRef);
                contentPack.updateSettingsIn(gitRepoDoc);
                gitRepoStore.writeDocument(gitRepoDoc);

                // Pull if necessary
                if (createGitRepoRequest.getAutoPull()) {
                    // Do the pull
                    List<Message> pullMessages = gitRepoStorageService.importDoc(gitRepoDoc);
                    messages.addAll(pullMessages);
                }

                // Tell the user it worked
                response = this.createOkResponse(contentPack, messages);

            } catch (IOException e) {
                response = this.createErrResponse(
                        "Error pulling files from Content Pack: " + e.getMessage(),
                        messages,
                        e);
            } catch (RuntimeException e) {
                response = this.createErrResponse(
                        "Error creating Content Pack: " + e.getMessage(),
                        messages,
                        e);
            }
        }

        return response;
    }

    /**
     * Creates the response to send back to the client if everything went ok.
     * @param cp The content pack we were trying to import.
     * @param messages The list of messages to send back.
     * @return The response to send back. Never null.
     */
    private AppStoreResponse createOkResponse(
            AppStoreContentPack cp,
            List<Message> messages) {

        var buf = new StringBuilder("Created '");
        buf.append(cp.getUiName());
        buf.append("'\n");
        for (var m : messages) {
            buf.append('\n');
            buf.append(m);
        }

        LOGGER.info("Created Content Pack: \n{}", buf);
        return new AppStoreResponse(true, buf.toString());
    }

    /**
     * Returns the response if something goes wrong.
     * @param errorMessage The error message to send back.
     * @param messages List of messages. Must not be null but can be empty.
     * @param cause The exception, if any. Can be null.
     * @return The response. Never null.
     */
    private AppStoreResponse createErrResponse(
            String errorMessage,
            List<Message> messages,
            Exception cause) {

        var buf = new StringBuilder(errorMessage);
        if (cause != null) {
            buf.append("\n    ");
            buf.append(cause.getMessage());
        }
        if (!messages.isEmpty()) {
            buf.append("\n\nAdditional information:");
            for (var m : messages) {
                buf.append("\n    ");
                buf.append(m);
            }
        }

        LOGGER.error("Error creating Content Pack: \n{}",
                buf,
                cause);

        return new AppStoreResponse(false, buf.toString());
    }

}
