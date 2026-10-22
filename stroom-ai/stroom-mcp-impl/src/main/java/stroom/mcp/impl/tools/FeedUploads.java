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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.StreamReader;
import stroom.data.shared.UploadDataRequest;
import stroom.data.store.api.DataService;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.StroomStreamException;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Sample data sent into a feed as the user, through Stroom's own receipt path (ported from the Stroom MCP server's
/// `utils/uploads.py` `send_to_feed` and `commands`).
///
/// The data is checked against Stroom's receipt checks for the feed as data posted to the datafeed is (the feed's
/// status, or the data receipt policy, whichever Stroom is configured with), then stored as Stroom's own Upload on
/// a feed's Data tab stores it ([DataService#upload]): as the user, who needs the Import Data permission and Edit on
/// the feed. A file on the user's machine reaches here through `/mcp/upload` ([SampleUploads]), never through the
/// model: VS Code's read_file cuts a line at 2,000 characters, and a 755 KB one-line JSON file arrived as three
/// records, the last completed with values the agent made up.
public class FeedUploads {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedUploads.class);

    /// The environment variable the commands read the user's credential from.
    public static final String KEY_VARIABLE = "STROOM_API_KEY";

    private final Provider<ResourceStore> resourceStoreProvider;
    private final Provider<DataService> dataServiceProvider;
    private final Provider<AttributeMapFilterFactory> filterFactoryProvider;
    private final StreamReader streamReader;
    private final SecurityContext securityContext;

    /// @param resourceStoreProvider Holds the data while Stroom stores it.
    /// @param dataServiceProvider   Stores it as Stroom's Upload does, as the user.
    /// @param filterFactoryProvider Stroom's receipt checks.
    /// @param streamReader          Finds the stream made.
    /// @param securityContext       Says who uploaded it.
    @Inject
    public FeedUploads(final Provider<ResourceStore> resourceStoreProvider,
                       final Provider<DataService> dataServiceProvider,
                       final Provider<AttributeMapFilterFactory> filterFactoryProvider,
                       final StreamReader streamReader,
                       final SecurityContext securityContext) {
        this.resourceStoreProvider = resourceStoreProvider;
        this.dataServiceProvider = dataServiceProvider;
        this.filterFactoryProvider = filterFactoryProvider;
        this.streamReader = streamReader;
        this.securityContext = securityContext;
    }

    /// Sends data to the feed, as the user, and finds the stream it made.
    ///
    /// @param feed       The feed's name.
    /// @param streamType The stream type to store it as, e.g. `Raw Events`.
    /// @param receipt    Its receipt headers (`EffectiveTime` among them for reference data), readable in XSLT with
    ///                   `stroom:meta()`.
    /// @param fileName   The file it came from, or what to call it.
    /// @param data       The data.
    /// @param limit      The most bytes to take.
    /// @return The receipt id, the stream id (null if it isn't visible yet) and the bytes sent.
    /// @throws TooLarge   if there are more than `limit` bytes (nothing is stored).
    /// @throws NoData     if there are none.
    /// @throws ToolRefusal if Stroom refuses or drops the data, or can't store it.
    public Sent send(final String feed,
                     final String streamType,
                     final Map<String, String> receipt,
                     final String fileName,
                     final InputStream data,
                     final long limit) {
        final long started = System.currentTimeMillis() - 1000;
        final Long effectiveMs = effectiveMs(receipt.get(StandardHeaderArguments.EFFECTIVE_TIME));
        checkReceipt(feed, streamType, receipt, fileName);
        final ResourceStore resourceStore = resourceStoreProvider.get();
        final ResourceKey key = resourceStore.createTempFile(fileName);
        final long bytes;
        try {
            bytes = copy(data, resourceStore.getTempFile(key), limit);
        } catch (final RuntimeException e) {
            resourceStore.deleteTempFile(key);
            throw e;
        }
        try {
            // Stroom's Upload deletes the file once it is stored.
            dataServiceProvider.get().upload(new UploadDataRequest(key, feed, streamType, effectiveMs,
                    metaData(receipt), fileName));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Upload to " + feed + " failed", e);
            throw new ToolRefusal("Stroom didn't store the data in feed " + feed + ": "
                                  + NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName()));
        }
        final Long streamId = streamMade(feed, streamType, started);
        return new Sent(receiptId(streamId), streamId, bytes);
    }

    /// The command for each file, for a POSIX shell and for PowerShell (where curl alone is Invoke-WebRequest): it
    /// posts the file from the user's disk, whole, as them, with their Stroom API key (or token) from
    /// [#KEY_VARIABLE]; the file's name is in the URL, so there is one quoted argument besides it.
    ///
    /// @param url   Where the files go, e.g. `https://stroom.example/mcp/upload/Xk3f9QpL2mWa`.
    /// @param files The files' paths, as the user's terminal sees them.
    /// @return Each file's `file`, `bash` and `powershell` commands.
    public static List<Map<String, String>> commands(final String url, final List<String> files) {
        final List<Map<String, String>> out = new ArrayList<>();
        for (final String name : files) {
            final String quoted = name.replace("\"", "\\\"");
            final String target = url + "?file=" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            final String args = "--data-binary \"@" + quoted + "\" \"" + target + "\"";
            final Map<String, String> command = new LinkedHashMap<>();
            command.put("file", name);
            command.put("bash", "curl -sS --fail-with-body -H \"Authorization: Bearer $" + KEY_VARIABLE + "\" "
                                + args);
            command.put("powershell", "curl.exe -sS --fail-with-body -H \"Authorization: Bearer $env:" + KEY_VARIABLE
                                      + "\" " + args);
            out.add(command);
        }
        return out;
    }

    // Stroom's receipt checks, as for data posted to the datafeed: the feed's status or the receipt policy.
    private void checkReceipt(final String feed,
                              final String streamType,
                              final Map<String, String> receipt,
                              final String fileName) {
        final AttributeMap attributes = new AttributeMap(receipt);
        attributes.put(StandardHeaderArguments.FEED, feed);
        attributes.put(StandardHeaderArguments.TYPE, streamType);
        attributes.put(StandardHeaderArguments.REMOTE_FILE, fileName);
        attributes.put("UploadedBy", securityContext.getUserIdentityForAudit());
        final boolean received;
        try {
            final AttributeMapFilter filter = filterFactoryProvider.get().create();
            received = filter.filter(attributes);
        } catch (final StroomStreamException e) {
            throw new ToolRefusal("Stroom refused data for feed " + feed + ": " + e.getMessage() + ". Nothing was "
                                  + "stored.");
        }
        if (!received) {
            throw new ToolRefusal("Stroom's receipt policy drops data for feed " + feed + " (" + streamType + "): "
                                  + "nothing was stored. Ask an administrator about the feed's receipt rules.");
        }
    }

    private static long copy(final InputStream data, final Path file, final long limit) {
        final byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (final OutputStream out = Files.newOutputStream(file)) {
            int read;
            while ((read = data.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new TooLarge(limit);
                }
                out.write(buffer, 0, read);
            }
        } catch (final IOException e) {
            throw new ToolRefusal("The data couldn't be read: " + e.getMessage());
        }
        if (total == 0) {
            throw new NoData();
        }
        return total;
    }

    // The receipt headers other than the effective time, which Stroom's Upload takes on its own, as header lines.
    private static String metaData(final Map<String, String> receipt) {
        final StringBuilder lines = new StringBuilder();
        receipt.forEach((name, value) -> {
            if (!StandardHeaderArguments.EFFECTIVE_TIME.equalsIgnoreCase(name)
                && !StandardHeaderArguments.FEED.equalsIgnoreCase(name)
                && !StandardHeaderArguments.TYPE.equalsIgnoreCase(name)
                && value != null) {
                lines.append(name).append(':').append(value).append('\n');
            }
        });
        return lines.toString();
    }

    private static Long effectiveMs(final String effectiveTime) {
        if (NullSafe.isBlankString(effectiveTime)) {
            return null;
        }
        try {
            return Instant.parse(effectiveTime.strip()).toEpochMilli();
        } catch (final DateTimeParseException e) {
            throw new ToolRefusal("effective_time must be an ISO 8601 UTC time, e.g. 2000-01-01T00:00:00.000Z, not '"
                                  + effectiveTime + "'");
        }
    }

    // The stream the upload made: the feed's newest of the type, created since the upload started.
    private Long streamMade(final String feed, final String streamType, final long started) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, streamType)
                .build();
        try {
            return NullSafe.list(NullSafe.get(streamReader.find(expression, 5, true), ResultPage::getValues))
                    .stream()
                    .map(MetaRow::getMeta)
                    .filter(meta -> meta.getCreateMs() >= started)
                    .map(Meta::getId)
                    .findFirst()
                    .orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Couldn't find the stream uploaded to " + feed, e);
            return null;
        }
    }

    // The receipt id Stroom gave the data, from the stream's receipt headers.
    private String receiptId(final Long streamId) {
        if (streamId == null) {
            return null;
        }
        try {
            final Object attributes = streamReader.streamAttributes(streamId).get("attributes");
            return attributes instanceof final Map<?, ?> headers
                    ? NullSafe.get(headers.get(StandardHeaderArguments.RECEIPT_ID), String::valueOf)
                    : null;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Couldn't read the receipt id of stream " + streamId, e);
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// What a send made.
    ///
    /// @param receiptId The receipt id Stroom gave the data, or null if it can't be read.
    /// @param streamId  The stream it made, or null if it isn't visible yet.
    /// @param bytes     The bytes sent.
    public record Sent(String receiptId, Long streamId, long bytes) {

        /// @return It as the Stroom MCP server gave it: `receipt_id`, `stream_id` and `bytes`.
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("receipt_id", receiptId);
            map.put("stream_id", streamId);
            map.put("bytes", bytes);
            return map;
        }
    }

    /// More data than may be sent.
    public static final class TooLarge extends ToolRefusal {

        /// @param limit The most bytes that may be sent.
        public TooLarge(final long limit) {
            super("Larger than " + (limit / (1024 * 1024)) + " MB: send a part of the file, or send it to Stroom "
                  + "directly");
        }
    }

    /// No data at all.
    public static final class NoData extends ToolRefusal {

        /// Says what to give.
        public NoData() {
            super("No data: give the file with --data-binary @<file>");
        }
    }
}
