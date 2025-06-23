package stroom.contentstore.impl;

import stroom.contentstore.shared.ContentStoreCreateGitRepoRequest;
import stroom.contentstore.shared.ContentStoreResponse;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreValueResponse;
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
import stroom.contentstore.shared.ContentStoreResource;
import stroom.util.shared.Severity;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
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
 * REST server-side implementation for the ContentStore stuff.
 */
@SuppressWarnings("unused")
@AutoLogged
public class ContentStoreResourceImpl implements ContentStoreResource {

    /** Where we get configuration from */
    private final Provider<ContentStoreConfig> config;

    /** The store used to create a GitRepo */
    private final GitRepoStore gitRepoStore;

    /** Provides access to the Explorer Tree */
    private final ExplorerService explorerService;

    /** Allows this system to automatically pull content */
    private final GitRepoStorageService gitRepoStorageService;

    /** The size of the buffer used to copy stuff around */
    private static final int IO_BUF_SIZE = 4096;

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentStoreResourceImpl.class);

    /**
     * Injected constructor.
     * @param config Where to get configuration data from.
     * @param gitRepoStore How to create the GitRepoDoc.
     * @param explorerService How to interact with the Explorer Tree
     * @param gitRepoStorageService How to pull content from Git
     */
    @SuppressWarnings("unused")
    @Inject
    public ContentStoreResourceImpl(final Provider<ContentStoreConfig> config,
                                    final GitRepoStore gitRepoStore,
                                    final ExplorerService explorerService,
                                    final GitRepoStorageService gitRepoStorageService) {
        this.config = config;
        this.gitRepoStore = gitRepoStore;
        this.explorerService = explorerService;
        this.gitRepoStorageService = gitRepoStorageService;
    }

    /**
     * REST method to return the list of content packs to the client.
     *
     * @return A list of content packs. Never returns null but may
     * return an empty list.
     */
    @SuppressWarnings("unused")
    @Override
    public ResultPage<ContentStoreContentPack> list(final PageRequest pageRequest) {

        // Pull out the existing GitRepos so we know what exists
        final List<DocRef> existingDocRefs = gitRepoStore.list();
        final ArrayList<GitRepoDoc> installedGitRepoDocs = new ArrayList<>(existingDocRefs.size());
        for (DocRef docRef : existingDocRefs) {
            // Not sure if store can return null, but handle it just in case...
            final GitRepoDoc doc = gitRepoStore.readDocument(docRef);
            if (doc != null) {
                installedGitRepoDocs.add(doc);
            }
        }

        // Grab YAML describing the content store
        final ObjectMapper mapper = YamlUtil.getMapper();

        final List<String> contentStoreUrls = config.get().getContentStoreUrls();
        final List<ContentStoreContentPack> contentPacks = new ArrayList<>();

        for (String appStoreUrl : contentStoreUrls) {
            LOGGER.info("Parsing appStore at '{}'", appStoreUrl);

            try {
                final URI uri = new URI(appStoreUrl);
                final InputStream istr = new BufferedInputStream(uri.toURL().openStream());
                final ContentStore cs = mapper.readValue(istr, ContentStore.class);

                // Fill in any extra data needed by the content packs
                final List<ContentStoreContentPack> listOfContentPacks = cs.getContentPacks();
                for (ContentStoreContentPack cp : listOfContentPacks) {
                    // Resolve icon link to SVG text
                    this.resolveSvgIcon(cp);

                    // Add the content store metadata
                    cp.setContentStoreMetadata(cs.getMeta());

                    // Check if content pack is installed or upgradable
                    cp.checkInstallationStatus(installedGitRepoDocs);
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
    private void resolveSvgIcon(final ContentStoreContentPack contentPack) {
        if (contentPack.getIconSvg() == null) {
            try {
                final byte[] buffer = new byte[IO_BUF_SIZE];
                final URI uri = new URI(contentPack.getIconUrl());
                final InputStream istr = new BufferedInputStream(uri.toURL().openStream());
                final ByteArrayOutputStream ostr = new ByteArrayOutputStream();

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
     * content store owner ID and content pack ID.
     * @param contentPack The content pack to check for a match.
     * @return true if the GitRepo already exists; false if not.
     */
    @Override
    public boolean exists(final ContentStoreContentPack contentPack) {
        final List<DocRef> existingDocRefs = gitRepoStore.list();
        for (DocRef existingDocRef : existingDocRefs) {
            final GitRepoDoc existingGitRepoDoc = gitRepoStore.readDocument(existingDocRef);
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
    public ContentStoreResponse create(final ContentStoreCreateGitRepoRequest createGitRepoRequest) {
        LOGGER.info("REST request to create GitRepo from Content Store: {}", createGitRepoRequest);

        // Return value
        ContentStoreResponse response;

        final List<Message> messages = new ArrayList<>();

        final ContentStoreContentPack contentPack = createGitRepoRequest.getContentPack();
        if (this.exists(contentPack)) {
            LOGGER.error("Content pack already exists within Stroom");
            response = new ContentStoreResponse(false, "Content pack already exists");
        } else {
            try {
                // Put the document into the Explorer Tree
                LOGGER.info("Creating DocPath from '{}'", contentPack.getStroomPath());
                final DocPath docPathToGitRepo = DocPath.fromPathString(contentPack.getStroomPath());
                final ExplorerNode parentNode = explorerService.ensureFolderPath(docPathToGitRepo,
                        PermissionInheritance.DESTINATION);
                final ExplorerNode gitRepoNode = explorerService.create(
                        GitRepoDoc.TYPE,
                        contentPack.getGitRepoName(),
                        parentNode,
                        PermissionInheritance.DESTINATION);

                // Update the GitRepoDoc
                final DocRef docRef = gitRepoNode.getDocRef();
                final GitRepoDoc gitRepoDoc = gitRepoStore.readDocument(docRef);
                contentPack.updateSettingsIn(gitRepoDoc);

                // Add credentials if necessary
                if (contentPack.getGitNeedsAuth()) {
                    messages.add(new Message(Severity.INFO, "Adding credentials for pull"));
                    gitRepoDoc.setUsername(createGitRepoRequest.getUsername());
                    gitRepoDoc.setPassword(createGitRepoRequest.getPassword());
                }

                // Write doc to DB
                gitRepoStore.writeDocument(gitRepoDoc);

                // Do the pull
                final List<Message> pullMessages = gitRepoStorageService.importDoc(gitRepoDoc);
                messages.addAll(pullMessages);

                // Tell the user it worked
                response = this.createOkResponse("Created", contentPack, messages);

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
     * @param operation String describing what we've done - Created, Upgraded etc.
     * @param cp The content pack we were trying to import.
     * @param messages The list of messages to send back.
     * @return The response to send back. Never null.
     */
    private ContentStoreResponse createOkResponse(
            final String operation,
            final ContentStoreContentPack cp,
            final List<Message> messages) {

        final StringBuilder buf = new StringBuilder(operation);
        buf.append(" '");
        buf.append(cp.getUiName());
        buf.append("'\n");
        for (Message m : messages) {
            buf.append('\n');
            buf.append(m);
        }

        LOGGER.info("{} Content Pack: \n{}", operation, buf);
        return new ContentStoreResponse(true, buf.toString());
    }

    /**
     * Returns the response if something goes wrong.
     * @param errorMessage The error message to send back.
     * @param messages List of messages. Can be null or empty.
     * @param cause The exception, if any. Can be null.
     * @return The response. Never null.
     */
    private ContentStoreResponse createErrResponse(
            final String errorMessage,
            final List<Message> messages,
            final Exception cause) {

        final StringBuilder buf = new StringBuilder(errorMessage);
        if (cause != null) {
            buf.append("\n    ");
            buf.append(cause.getMessage());
        }
        if (messages != null && !messages.isEmpty()) {
            buf.append("\n\nAdditional information:");
            for (Message m : messages) {
                buf.append("\n    ");
                buf.append(m);
            }
        }

        LOGGER.error("Error creating Content Pack: \n{}",
                buf,
                cause);

        return new ContentStoreResponse(false, buf.toString());
    }

    @Override
    public ContentStoreValueResponse<Boolean> checkContentUpgradeAvailable(final ContentStoreContentPack contentPack) {
        LOGGER.info("Checking for upgrades for {}", contentPack.getUiName());

        try {
            // Find a matching GitRepoDoc
            GitRepoDoc gitRepoDoc = null;
            final List<DocRef> existingDocRefs = gitRepoStore.list();
            for (DocRef existingDocRef : existingDocRefs) {
                final GitRepoDoc existingGitRepoDoc = gitRepoStore.readDocument(existingDocRef);
                if (contentPack.matches(existingGitRepoDoc)) {
                    gitRepoDoc = existingGitRepoDoc;
                    break;
                }
            }

            // Check if updates are available for this doc
            Boolean retval = Boolean.FALSE;
            if (gitRepoDoc != null) {
                retval = gitRepoStorageService.areUpdatesAvailable(gitRepoDoc);
            }

            return new ContentStoreValueResponse<>(
                    true,
                    retval,
                    null);

        } catch (IOException e) {

            final StringBuilder buf =
                    new StringBuilder("Error checking whether the content pack \"");
            buf.append(contentPack.getUiName());
            buf.append("\" can be upgraded:\n    ");
            buf.append(e.getMessage());

            LOGGER.error("{}",
                    buf,
                    e);

            return new ContentStoreValueResponse<>(false,
                    null,
                    buf.toString());
        }
    }

    @Override
    public ContentStoreResponse upgradeContentPack(ContentStoreContentPack contentPack) {
        LOGGER.info("Upgrading {}", contentPack.getUiName());
        ArrayList<Message> messages = new ArrayList<>();

        try {
            // Find a matching GitRepoDoc
            GitRepoDoc gitRepoDoc = null;
            final List<DocRef> existingDocRefs = gitRepoStore.list();
            for (DocRef existingDocRef : existingDocRefs) {
                final GitRepoDoc existingGitRepoDoc = gitRepoStore.readDocument(existingDocRef);
                if (contentPack.matches(existingGitRepoDoc)) {
                    gitRepoDoc = existingGitRepoDoc;
                    break;
                }
            }

            if (gitRepoDoc == null) {
                throw new IOException("Cannot upgrade Content Pack as it is not installed");
            }

            // Do a pack upgrade
            // i.e. copy settings from Content Pack into GitRepo
            contentPack.updateSettingsIn(gitRepoDoc);

            // Pull down any new content
            messages.addAll(this.gitRepoStorageService.importDoc(gitRepoDoc));

            return createOkResponse("Upgraded", contentPack, messages);

        } catch (IOException e) {
            return this.createErrResponse("Error upgrading content pack",
                    messages,
                    e);
        }

    }

}
