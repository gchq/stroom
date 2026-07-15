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
import stroom.aws.s3.client.S3Util;
import stroom.aws.s3.shared.S3EventResource.S3EventNotificationRequest;
import stroom.aws.s3.shared.S3Location;
import stroom.cache.api.TemplateCache;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.handler.ForwardS3Config.NotificationType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.FeedKey;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.util.shared.string.CIKeys;
import stroom.util.string.TemplateUtil.Template;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ForwardS3DestinationImpl implements ForwardS3Destination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardS3DestinationImpl.class);

    private static final CIKey FEED_VAR = CIKeys.FEED;
    private static final CIKey TYPE_VAR = CIKeys.TYPE;
    private static final CIKey RECEIPT_ID_VAR = CIKey.internStaticKey(StandardHeaderArguments.RECEIPT_ID);
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
    private final RemoteS3EventClient remoteS3EventClient;

    public ForwardS3DestinationImpl(final String destinationName,
                                    final ForwardS3Config forwardS3Config,
                                    final DownstreamHostConfig downstreamHostConfig,
                                    final S3ClientPool s3ClientPool,
                                    final TemplateCache templateCache,
                                    final S3MetaFieldsMapper s3MetaFieldsMapper,
                                    final CleanupDirQueue cleanupDirQueue,
                                    final RemoteS3EventClient remoteS3EventClient) {
        this.destinationName = destinationName;
        this.forwardS3Config = forwardS3Config;
        this.downstreamHostConfig = downstreamHostConfig;
        this.templateCache = templateCache;
        this.s3MetaFieldsMapper = s3MetaFieldsMapper;
        this.cleanupDirQueue = cleanupDirQueue;
        this.s3ClientHelper = new S3ClientHelper(forwardS3Config.getClientConfig(), s3ClientPool);
        this.remoteS3EventClient = remoteS3EventClient;
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
            final String bucketName = createBucketName(feedKey, attributeMap);
            final String key = createKey(feedKey, attributeMap);

            LOGGER.debug("add() - sourceDir: {}, feedKey: {}, bucketName: {}, keyPattern: {}",
                    sourceDir, feedKey, bucketName, key);

            final Map<String, String> s3Tags = new HashMap<>(2);
            NullSafe.consumeNonBlankString(feedKey.feed(), val ->
                    s3Tags.put(S3ClientHelper.FEED_TAG_KEY, val));
            NullSafe.consumeNonBlankString(feedKey.type(), val ->
                    s3Tags.put(S3ClientHelper.STREAM_TYPE_TAG_KEY, val));

            final Map<CIKey, String> s3MetaData = buildS3MetaData(attributeMap);

            try {
                try {
                    s3ClientHelper.upload(
                            bucketName,
                            key,
                            s3Tags,
                            s3MetaData,
                            DEFAULT_UPLOAD_PROPERTIES,
                            zipFile);
                } catch (final Exception e) {
                    throw new RuntimeException(LogUtil.message(
                            "Error uploading file {} to S3, bucketName: {}, key: {} - {}",
                            zipFile, bucketName, key, LogUtil.exceptionMessage(e)), e);
                }

                LOGGER.debug("add() - Uploaded {} to S3, bucket: {}, key: {}", zipFile, bucketName, key);

                // If not, we rely on Event Notifications from S3+SQS
                if (NotificationType.REST == forwardS3Config.getNotificationType()) {
                    final S3Location s3Location = new S3Location(
                            forwardS3Config.getClientConfig().getRegion(),
                            bucketName,
                            key);
                    final S3EventNotificationRequest request = new S3EventNotificationRequest(s3Location, attributeMap);
                    remoteS3EventClient.sendNotification(request);
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

    private String createBucketName(final FeedKey feedKey, final AttributeMap attributeMap) {
        final String bucketName = forwardS3Config.getClientConfig().getBucketName();
        NullSafe.requireNonBlankString(bucketName);
        final String bucket = applyTemplate(bucketName, feedKey, attributeMap);
        final String cleaned = S3Util.cleanKeyName(bucket);
        LOGGER.debug("createKey() - bucket: '{}', cleaned: '{}'", bucket, cleaned);
        return cleaned;
    }

    private String createKey(final FeedKey feedKey, final AttributeMap attributeMap) {
        final String keyPattern = forwardS3Config.getClientConfig().getKeyPattern();
        NullSafe.requireNonBlankString(keyPattern);
        final String key = applyTemplate(keyPattern, feedKey, attributeMap);
        final String cleaned = S3Util.cleanKeyName(key);
        LOGGER.debug("createKey() - key: '{}', cleaned: '{}'", key, cleaned);
        return cleaned;
    }

    private String applyTemplate(final String templateStr,
                                 final FeedKey feedKey,
                                 final AttributeMap attributeMap) {
        final Template template = templateCache.getTemplate(templateStr);
        // Use now() for time replacements. When one of: rolling, agg splitting and record splitting is used,
        // the time vars can distinguish multiple files coming from the same stream
        // It doesn't make sense to have a sequence numbers in the template as there will potentially
        // be multiple nodes uploading to the same s3 bucket, so a seq number would need to be globally
        // unique. Better to use a UUID or receiptId instead.
        final String output = template.buildExecutor()
                .addStandardTimeReplacements()
                .addUuidReplacement(false)
                .addLazyReplacement(FEED_VAR, feedKey::feed)
                .addLazyReplacement(TYPE_VAR, feedKey::type)
                .addLazyReplacement(RECEIPT_ID_VAR, () ->
                        attributeMap.get(RECEIPT_ID_VAR))
                .execute();

        LOGGER.debug("applyTemplate() - template: '{}', output: '{}', feedKey: '{}', attributeMap: '{}'",
                template, output, feedKey, attributeMap);
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
