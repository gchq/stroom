/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.proxy.app.handler;


import stroom.aws.s3.client.S3ClientHelper;
import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaFieldsMapper;
import stroom.aws.s3.client.S3UploadProperties;
import stroom.aws.s3.shared.S3EventResource;
import stroom.aws.s3.shared.S3EventResource.S3EventRequest;
import stroom.aws.s3.shared.S3Location;
import stroom.cache.api.TemplateCache;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.handler.ForwardS3Config.NotificationType;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.FeedKey;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.string.CIKey;
import stroom.util.string.TemplateUtil.Template;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ForwardS3DestinationImpl implements ForwardS3Destination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardS3DestinationImpl.class);
    private static final CIKey FEED_VAR = CIKey.internStaticKey("feed");
    private static final CIKey TYPE_VAR = CIKey.internStaticKey("type");
    private static final S3UploadProperties DEFAULT_UPLOAD_PROPERTIES = S3UploadProperties.builder()
            .contentType("application/zip")
            .build();

    private final String destinationName;
    private final ForwardS3Config forwardS3Config;
    private final TemplateCache templateCache;
    private final DownstreamHostConfig downstreamHostConfig;
    private final S3ClientHelper s3ClientHelper;
    private final S3MetaFieldsMapper s3MetaFieldsMapper;
    private final CleanupDirQueue cleanupDirQueue;
    private final Client jerseyClient;

    public ForwardS3DestinationImpl(final String destinationName,
                                    final ForwardS3Config forwardS3Config,
                                    final DownstreamHostConfig downstreamHostConfig,
                                    final S3ClientPool s3ClientPool,
                                    final TemplateCache templateCache,
                                    final S3MetaFieldsMapper s3MetaFieldsMapper,
                                    final CleanupDirQueue cleanupDirQueue,
                                    final JerseyClientFactory jerseyClientFactory) {
        this.destinationName = destinationName;
        this.forwardS3Config = forwardS3Config;
        this.downstreamHostConfig = downstreamHostConfig;
        this.templateCache = templateCache;
        this.s3MetaFieldsMapper = s3MetaFieldsMapper;
        this.cleanupDirQueue = cleanupDirQueue;
        this.s3ClientHelper = new S3ClientHelper(forwardS3Config.getClientConfig(), s3ClientPool);
        this.jerseyClient = jerseyClientFactory.getNamedClient(JerseyClientName.DOWNSTREAM);
    }

    @Override
    public void add(final Path sourceDir) {
        try {
            final FileGroup fileGroup = new FileGroup(sourceDir);
            final Path zipFile = fileGroup.getZip();
            LOGGER.debug("'{}' - add(), dir: {}, zip: {}", destinationName, sourceDir, zipFile);
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
            // Make sure we tell the destination we are sending zip data.
            attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

            final FeedKey feedKey = FeedKey.of(attributeMap.get(FEED_VAR.get()), attributeMap.get(TYPE_VAR.get()));
            final String bucketName = createBucketName(feedKey);
            final String key = createKey(feedKey);

            LOGGER.debug("add() - sourceDir: {}, feedKey: {}, bucketName: {}, keyPattern: {}",
                    sourceDir, feedKey, bucketName, key);

            final Map<String, String> s3Tags = Map.of(
                    S3ClientHelper.FEED_TAG_KEY, feedKey.feed(),
                    S3ClientHelper.STREAM_TYPE_TAG_KEY, feedKey.type());
            final Map<CIKey, String> s3MetaData = buildS3MetaData(attributeMap);

            try {
                s3ClientHelper.upload(
                        bucketName,
                        key,
                        s3Tags,
                        s3MetaData,
                        DEFAULT_UPLOAD_PROPERTIES,
                        zipFile);

                LOGGER.debug("add() - Uploaded {} to S3, bucket: {}, key: {}", zipFile, bucketName, key);

                // If not, we rely on S3 Event Notifications
                if (NotificationType.REST == forwardS3Config.getNotificationType()) {
                    sendRestNotification(bucketName, key, attributeMap);
                }

                // Success, so remove the source file.
                cleanupDirQueue.add(sourceDir);
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error uploading file {} to S3, bucketName: {}, key: {} - {}",
                        zipFile, bucketName, key, LogUtil.exceptionMessage(e)), e);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void sendRestNotification(final String bucketName,
                                      final String key,
                                      final Map<String, String> meta) {
        final S3Location s3Location = new S3Location(
                forwardS3Config.getClientConfig().getRegion(),
                bucketName,
                key);
        final S3EventRequest request = new S3EventRequest(s3Location, meta);
        final String uriPath = ResourcePaths.buildAuthenticatedApiPath(
                S3EventResource.BASE_RESOURCE_PATH,
                S3EventResource.NOTIFY_PATH_PART);
        final String uri = downstreamHostConfig.createUri(uriPath);
        LOGGER.debug("sendRestNotification() - uri: {}, request: {}", uri, request);

        final WebTarget target = jerseyClient.target(uri);
        try (final Response response = target.request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request))) {

            if (response.getStatus() != HttpServletResponse.SC_OK) {
                final String error;
                try {
                    error = response.readEntity(String.class);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
                throw new RuntimeException(LogUtil.message(
                        "Error sending S3 notification {} to {} - {}", request, uri, error));
            }
        }
    }

    private Map<CIKey, String> buildS3MetaData(final AttributeMap attributeMap) {

        final Map<CIKey, String> map = attributeMap.entrySet()
                .stream()
                .map(entry -> {
                    final Optional<CIKey> optKey = s3MetaFieldsMapper.getS3Key(entry.getKey())
                            .map(CIKey::of);
                    if (optKey.isPresent()) {
                        return Map.entry(optKey.get(), entry.getValue());
                    } else {
                        LOGGER.warn("No S3 field mapping for key '{}', value: '{}'", entry.getKey(), entry.getValue());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        LOGGER.debug("buildS3MetaData() - map: {}", map);
        return map;
    }

    private String createBucketName(final FeedKey feedKey) {
        final String bucketName = forwardS3Config.getClientConfig().getBucketName();
        NullSafe.requireNonBlankString(bucketName);
        return applyTemplate(bucketName, feedKey);
    }

    private String createKey(final FeedKey feedKey) {
        final String keyPattern = forwardS3Config.getClientConfig().getKeyPattern();
        NullSafe.requireNonBlankString(keyPattern);
        return applyTemplate(keyPattern, feedKey);
    }

    private String applyTemplate(final String templateStr,
                                 final FeedKey feedKey) {
        final Template template = templateCache.getTemplate(templateStr);
        // Use now() for time replacements. When one of: rolling, agg splitting and record splitting is used,
        // the time vars can distinguish multiple files coming from the same stream
        final String output = template.buildExecutor()
                .addStandardTimeReplacements()
                .addUuidReplacement(false)
                .addLazyReplacement(FEED_VAR, feedKey::feed)
                .addLazyReplacement(TYPE_VAR, feedKey::type)
//                .addLazyReplacement(ID_VAR, () -> String.valueOf(meta.getId()))
//                .addLazyReplacement(ID_PATH_VAR, () -> getIdPath(meta.getId()))
//                .addLazyReplacement(ID_PADDED_VAR, () -> padId(meta.getId()))
                .execute();

        LOGGER.debug("applyTemplate() - template: '{}', output: '{}", template, output);
        return output;
    }

    @Override
    public String getName() {
        return destinationName;
    }

    @Override
    public String getDestinationDescription() {
        return forwardS3Config.getDestinationDescription();
    }
}
