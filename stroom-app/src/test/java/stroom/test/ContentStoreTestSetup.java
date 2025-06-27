package stroom.test;

import stroom.contentstore.impl.ContentStoreConfig;
import stroom.contentstore.impl.ContentStoreResourceImpl;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.contentstore.shared.ContentStoreCreateGitRepoRequest;
import stroom.contentstore.shared.ContentStoreResponse;
import stroom.test.common.ProjectPathUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Pulls Content Store items into Stroom for integration tests
 * and development.
 * Called from SetupSampleData.
 */
public class ContentStoreTestSetup {

    /** Thing to do the actual import */
    private final ContentStoreResourceImpl contentStoreResource;

    /** Stuff in the Content Store */
    private final List<ContentStoreContentPackWithDynamicState> contentStoreContents;

    /** Every ID in the content store - SetupSampleData installs all of them */
    private final List<String> samplePackIds = new ArrayList<>();

    /**
     * Standard packs, used by integration tests.
     */
    public static final List<String> STANDARD_PACK_IDS = Arrays.asList(
            "core-xml-schemas",
            "event-logging-xml-schema",
            "standard-pipelines",
            "template-pipelines",
            "state",
            "planb"
    );

    /**
     * Constructor - injected.
     * @param contentStoreResource Injected service for managing content stores and packs.
     */
    @Inject
    public ContentStoreTestSetup(
            final ContentStoreConfig contentStoreConfig,
            final ContentStoreResourceImpl contentStoreResource) {

        // Hack to force the content store config to use our content store config file
        contentStoreConfig.resetUrlsToFile(
                ProjectPathUtil.getRepoRoot(),
                "content-store-sample-data.yml");

        // Store the backend
        this.contentStoreResource = contentStoreResource;

        // Get all the items in one go
        PageRequest pageRequest = new PageRequest(0, Integer.MAX_VALUE);
        ResultPage<ContentStoreContentPackWithDynamicState> page = contentStoreResource.list(pageRequest);

        this.contentStoreContents = page.stream().toList();

        // List out all the IDs so we can install them if necessary
        for (ContentStoreContentPackWithDynamicState cpds : contentStoreContents) {
            this.samplePackIds.add(cpds.getContentPack().getId());
        }
    }

    /**
     * Installs the content pack with the given ID.
     * @param contentPackId The ID of the content pack.
     * @throws RuntimeException If something goes wrong.
     */
    public void install(final String contentPackId) throws RuntimeException {

        // Find the content pack
        ContentStoreContentPack cp = null;
        for (ContentStoreContentPackWithDynamicState cpds : this.contentStoreContents) {
            if (cpds.getContentPack().getId().equals(contentPackId)) {
                cp = cpds.getContentPack();
                break;
            }
        }

        if (cp == null) {
            throw new RuntimeException("Cannot find content pack with ID '" + contentPackId + "'");
        }

        if (cp.getGitNeedsAuth()) {
            throw new RuntimeException("Cannot import content pack with ID '"
                                  + contentPackId
                                  + "': authentication is required");
        }

        // Install the pack
        ContentStoreCreateGitRepoRequest request = new ContentStoreCreateGitRepoRequest(cp, null, null);
        ContentStoreResponse response = contentStoreResource.create(request);
        if (!response.isOk()) {
            throw new RuntimeException("Couldn't create content pack with ID '"
            + contentPackId + "': " + response.getMessage());
        }
    }

    /**
     * Installs a number of content packs.
     * @param contentPackIds Collection of content pack IDs.
     * @throws RuntimeException If something goes wrong.
     */
    public void install(final Collection<String> contentPackIds) throws RuntimeException {
        for (String id : contentPackIds) {
            install(id);
        }
    }

    /**
     * Installs all the content packs for SetupSampleData.
     * @throws RuntimeException if something goes wrong.
     */
    public void installSampleDataPacks() throws RuntimeException {
        install(samplePackIds);
    }

    /**
     * Installs the standard packs, as used in integration tests.
     */
    public void installStandardPacks() throws RuntimeException {
        install(STANDARD_PACK_IDS);
    }

    /**
     * Installs visualisations.
     */
    public void installVisualisations() throws RuntimeException {
        install("stroom-visualisations");
    }
}
