/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.store.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.data.shared.UploadDataRequest;
import stroom.data.shared.UploadFeedSource;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class TestDataServiceImpl {

    private static final String SELECTED_FEED = "SELECTED_FEED";
    private static final String FEED_A = "FEED_A";
    private static final String FEED_B = "FEED_B";

    @Mock
    private ResourceStore resourceStore;
    @Mock
    private DataUploadTaskHandler dataUploadTaskHandler;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private DocFinder docFinder;

    @TempDir
    private Path tempDir;

    private DataServiceImpl dataService;

    @BeforeEach
    void beforeEach() {
        dataService = createDataService();

        // Run the secured code as if the user has the app permission.
        Mockito.lenient()
                .when(securityContext.secureResult(any(AppPermission.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    }

    @Test
    void testZipFeeds_allFeedsExist() throws IOException {
        givenFeeds(Set.of(SELECTED_FEED, FEED_A, FEED_B), Set.of());
        final UploadDataRequest request = givenUpload("test.zip", createZip(FEED_A, FEED_B),
                UploadFeedSource.ZIP_FEEDS);

        dataService.upload(request);

        verifyUploaded(UploadFeedSource.ZIP_FEEDS);
    }

    @Test
    void testZipFeeds_missingFeeds() throws IOException {
        givenFeeds(Set.of(SELECTED_FEED), Set.of());
        final UploadDataRequest request = givenUpload("test.zip", createZip(FEED_A, FEED_B),
                UploadFeedSource.ZIP_FEEDS);

        assertThatThrownBy(() -> dataService.upload(request))
                .isInstanceOf(EntityServiceException.class)
                .isNotInstanceOf(PermissionException.class)
                .hasMessage("The zip names 2 feeds that don't exist on this system: 'FEED_A', 'FEED_B'");

        verifyNotUploaded(request);
    }

    @Test
    void testZipFeeds_oneMissingFeed() throws IOException {
        givenFeeds(Set.of(SELECTED_FEED, FEED_A), Set.of());
        final UploadDataRequest request = givenUpload("test.zip", createZip(FEED_A, FEED_B),
                UploadFeedSource.ZIP_FEEDS);

        assertThatThrownBy(() -> dataService.upload(request))
                .isInstanceOf(EntityServiceException.class)
                .hasMessage("The zip names 1 feed that doesn't exist on this system: 'FEED_B'");

        verifyNotUploaded(request);
    }

    @Test
    void testZipFeeds_noPermission() throws IOException {
        givenFeeds(Set.of(SELECTED_FEED, FEED_A, FEED_B), Set.of(FEED_B));
        final UploadDataRequest request = givenUpload("test.zip", createZip(FEED_A, FEED_B),
                UploadFeedSource.ZIP_FEEDS);

        assertThatThrownBy(() -> dataService.upload(request))
                .isInstanceOf(PermissionException.class)
                .hasMessage("User does not have permission to update 1 feed named in the zip: 'FEED_B'");

        verifyNotUploaded(request);
    }

    @Test
    void testZipFeeds_missingFeedAndNoPermission() throws IOException {
        givenFeeds(Set.of(SELECTED_FEED, FEED_A), Set.of(FEED_A));
        final UploadDataRequest request = givenUpload("test.zip", createZip(FEED_A, FEED_B),
                UploadFeedSource.ZIP_FEEDS);

        assertThatThrownBy(() -> dataService.upload(request))
                .isInstanceOf(EntityServiceException.class)
                .isNotInstanceOf(PermissionException.class)
                .hasMessage("""
                        The zip names 1 feed that doesn't exist on this system: 'FEED_B'
                        You do not have permission to update 1 feed named in the zip: 'FEED_A'""");

        verifyNotUploaded(request);
    }

    @Test
    void testZipFeeds_unreadableZip() throws IOException {
        givenFeeds(Set.of(SELECTED_FEED), Set.of());
        final Path file = tempDir.resolve("not_a_zip.zip");
        Files.writeString(file, "This is not a zip");
        final UploadDataRequest request = givenUpload("test.zip", file, UploadFeedSource.ZIP_FEEDS);

        assertThatThrownBy(() -> dataService.upload(request))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageStartingWith("Unable to read the feed names in the zip");

        verifyNotUploaded(request);
    }

    @Test
    void testZipFeeds_notAZipFile() throws IOException {
        // Only zips have .meta/.manifest entries, so a flat file isn't read for feed names.
        givenFeeds(Set.of(SELECTED_FEED), Set.of());
        final Path file = tempDir.resolve("test.dat");
        Files.writeString(file, "DATA");
        final UploadDataRequest request = givenUpload("test.dat", file, UploadFeedSource.ZIP_FEEDS);

        dataService.upload(request);

        verifyUploaded(UploadFeedSource.ZIP_FEEDS);
    }

    @Test
    void testSelectedFeed_zipFeedsNotChecked() throws IOException {
        // The zip's feeds are ignored, so they don't need to exist.
        givenFeeds(Set.of(SELECTED_FEED), Set.of());
        final UploadDataRequest request = givenUpload("test.zip", createZip(FEED_A, FEED_B),
                UploadFeedSource.SELECTED_FEED);

        dataService.upload(request);

        verifyUploaded(UploadFeedSource.SELECTED_FEED);
        Mockito.verify(docFinder, Mockito.never()).findByName(FeedDoc.TYPE, FEED_A);
        Mockito.verify(docFinder, Mockito.never()).findByName(FeedDoc.TYPE, FEED_B);
    }

    @Test
    void testSelectedFeedMissing() throws IOException {
        givenFeeds(Set.of(), Set.of());
        final UploadDataRequest request = givenUpload("test.zip", createZip(FEED_A),
                UploadFeedSource.ZIP_FEEDS);

        assertThatThrownBy(() -> dataService.upload(request))
                .hasMessage("Unable to find feed document with name SELECTED_FEED");

        Mockito.verifyNoInteractions(dataUploadTaskHandler);
    }

    /// Sets up the feeds that exist and the feeds that the user can't edit.
    private void givenFeeds(final Set<String> existingFeedNames,
                            final Set<String> noPermissionFeedNames) {
        Mockito.lenient()
                .when(docFinder.findByName(eq(FeedDoc.TYPE), anyString()))
                .thenAnswer(invocation -> {
                    final String feedName = invocation.getArgument(1);
                    return existingFeedNames.contains(feedName)
                            ? List.of(createFeedDocRef(feedName))
                            : List.of();
                });
        Mockito.lenient()
                .when(securityContext.hasDocumentPermission(any(DocRef.class), eq(DocumentPermission.EDIT)))
                .thenAnswer(invocation -> {
                    final DocRef docRef = invocation.getArgument(0);
                    return !noPermissionFeedNames.contains(docRef.getName());
                });
    }

    private UploadDataRequest givenUpload(final String fileName,
                                          final Path file,
                                          final UploadFeedSource feedSource) {
        final ResourceKey resourceKey = new ResourceKey(UUID.randomUUID().toString(), fileName);
        Mockito.lenient()
                .when(resourceStore.getTempFile(resourceKey))
                .thenReturn(file);
        return new UploadDataRequest(
                resourceKey,
                SELECTED_FEED,
                StreamTypeNames.RAW_EVENTS,
                null,
                null,
                fileName,
                feedSource);
    }

    private void verifyUploaded(final UploadFeedSource feedSource) {
        Mockito.verify(dataUploadTaskHandler).uploadData(
                anyString(),
                any(Path.class),
                eq(SELECTED_FEED),
                eq(StreamTypeNames.RAW_EVENTS),
                any(),
                any(),
                eq(feedSource));
    }

    private void verifyNotUploaded(final UploadDataRequest request) {
        Mockito.verifyNoInteractions(dataUploadTaskHandler);
        // The uploaded file is still cleaned up.
        Mockito.verify(resourceStore).deleteTempFile(request.getKey());
    }

    /// Creates a zip with one stream per feed name, each with a `.meta` naming its feed.
    private Path createZip(final String... feedNames) throws IOException {
        final Path zip = Files.createTempFile(tempDir, "test", ".zip");
        try (final OutputStream outputStream = Files.newOutputStream(zip);
                final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (int i = 0; i < feedNames.length; i++) {
                final String baseName = String.format("%03d", i + 1);
                final Map<String, String> entries = Map.of(
                        baseName + ".meta", "Feed:" + feedNames[i] + "\n",
                        baseName + ".dat", "DATA");
                for (final Map.Entry<String, String> entry : entries.entrySet()) {
                    zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                    zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zipOutputStream.closeEntry();
                }
            }
        }
        return zip;
    }

    private static DocRef createFeedDocRef(final String feedName) {
        return DocRef.builder()
                .type(FeedDoc.TYPE)
                .uuid(UUID.randomUUID().toString())
                .name(feedName)
                .build();
    }

    private DataServiceImpl createDataService() {
        // Only the dependencies used by upload() are needed.
        return new DataServiceImpl(
                resourceStore,
                dataUploadTaskHandler,
                null,
                null,
                null,
                securityContext,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                docFinder,
                null);
    }
}
