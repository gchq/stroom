/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.aws.s3.impl;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.docref.DocRef;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.writer.AbstractAppender;
import stroom.pipeline.writer.Output;
import stroom.pipeline.writer.OutputFactory;
import stroom.pipeline.writer.OutputProxy;
import stroom.svg.shared.SvgImage;
import stroom.util.io.CompressionUtil;
import stroom.util.io.PathCreator;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Joins text instances into a single text instance.
 */
@ConfigurableElement(
        type = "S3Appender",
        description = """
                A destination used to write an output stream to an S3 bucket.
                """,
        category = Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.DOCUMENT_S3)
public class S3Appender extends AbstractAppender {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Appender.class);
    private static final String DEFAULT_USE_COMPRESSION_PROP_VALUE = "true";
    private static final String DEFAULT_COMPRESSION_METHOD_PROP_VALUE = CompressorStreamFactory.GZIP;

    private final S3AppenderTempDir s3AppenderTempDir;
    private final PathCreator pathCreator;
    private final OutputFactory outputFactory;
    private final S3ClientConfigCache s3ClientConfigCache;
    private final MetaDataHolder metaDataHolder;
    private final MetaHolder metaHolder;
    private DocRef s3ConfigRef;
    private String bucketNamePattern;
    private String keyNamePattern;
    private S3ClientConfig s3ClientConfig;

    @Inject
    public S3Appender(final ErrorReceiverProxy errorReceiverProxy,
                      final MetaDataHolder metaDataHolder,
                      final MetaHolder metaHolder,
                      final S3AppenderTempDir s3AppenderTempDir,
                      final PathCreator pathCreator,
                      final S3ClientConfigCache s3ClientConfigCache) {
        super(errorReceiverProxy);
        this.s3AppenderTempDir = s3AppenderTempDir;
        this.pathCreator = pathCreator;
        this.s3ClientConfigCache = s3ClientConfigCache;
        this.metaDataHolder = metaDataHolder;
        this.metaHolder = metaHolder;
        outputFactory = new OutputFactory(metaDataHolder);

        // Ensure outputStreamSupport has the defaults for S3Appender
        //noinspection ConstantValue
        setUseCompression(Boolean.parseBoolean(DEFAULT_USE_COMPRESSION_PROP_VALUE));
        setCompressionMethod(DEFAULT_COMPRESSION_METHOD_PROP_VALUE);
    }

    @Override
    public void startProcessing() {
        if (s3ConfigRef == null) {
            fatal("No S3 config has been provided for S3 appender.");
        }

        final Optional<S3ClientConfig> optional = s3ClientConfigCache.get(s3ConfigRef);
        if (optional.isEmpty()) {
            fatal("Unable to load S3 client config from " + s3ConfigRef);
        } else {
            s3ClientConfig = optional.get();
        }

        super.startProcessing();
    }

    @Override
    protected Output createOutput() throws IOException {
        try {
            final Path tempFile = s3AppenderTempDir.createTempFile();
            LOGGER.trace("Using temp file {}", tempFile);

            // Get a writer for the new lock file.
            final Output output = outputFactory
                    .create(new BufferedOutputStream(Files.newOutputStream(tempFile)));

            return new OutputProxy(output) {
                @Override
                public void close() throws IOException {
                    super.close();

                    try {
                        final S3Manager s3Manager = new S3Manager(pathCreator, s3ClientConfig);
                        final String bucketNamePattern = NullSafe
                                .nonBlank(S3Appender.this.bucketNamePattern)
                                .orElse(s3Manager.getBucketNamePattern());
                        final String keyNamePattern = NullSafe
                                .nonBlank(S3Appender.this.keyNamePattern)
                                .orElse(s3Manager.getKeyNamePattern());

                        // Upload to S3
                        // Upload the zip to S3.
                        final Meta meta = metaHolder.getMeta();
                        final AttributeMap attributeMap = metaDataHolder.getMetaData();

                        s3Manager.upload(bucketNamePattern, keyNamePattern, meta, attributeMap, tempFile);
                    } catch (final RuntimeException e) {
                        fatal(e.getMessage(), e);
                    } finally {
                        Files.deleteIfExists(tempFile);
                    }
                }
            };

        } catch (final RuntimeException e) {
            error(e.getMessage(), e);
            throw new IOException(e.getMessage(), e);
        }
    }

    @PipelineProperty(description = "The S3 bucket config to use.", displayPriority = 1)
    @PipelinePropertyDocRef(types = S3ConfigDoc.TYPE)
    public void setS3Config(final DocRef s3ConfigRef) {
        this.s3ConfigRef = s3ConfigRef;
    }

    @PipelineProperty(
            description = "Set the bucket name pattern if you want to override the one provided by the S3 config.",
            defaultValue = "",
            displayPriority = 2)
    public void setBucketNamePattern(final String bucketNamePattern) {
        this.bucketNamePattern = bucketNamePattern;
    }

    @PipelineProperty(
            description = "Set the key name pattern if you want to override the one provided by the S3 config.",
            defaultValue = "${type}/${year}/${month}/${day}/${idPath}/${feed}/${idPadded}.gz",
            displayPriority = 3)
    public void setKeyNamePattern(final String keyNamePattern) {
        this.keyNamePattern = keyNamePattern;
    }

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "When the current output object exceeds this size it will be closed and a new one created.",
            displayPriority = 4)
    public void setRollSize(final String size) {
        super.setRollSize(size);
    }

    @PipelineProperty(
            description = "Choose if you want to split aggregated streams into separate output objects.",
            defaultValue = "false",
            displayPriority = 5)
    public void setSplitAggregatedStreams(final boolean splitAggregatedStreams) {
        super.setSplitAggregatedStreams(splitAggregatedStreams);
    }

    @PipelineProperty(
            description = "Choose if you want to split individual records into separate output objects.",
            defaultValue = "false",
            displayPriority = 6)
    public void setSplitRecords(final boolean splitRecords) {
        super.setSplitRecords(splitRecords);
    }

    @PipelineProperty(
            description = "Apply compression to output objects.",
            defaultValue = DEFAULT_USE_COMPRESSION_PROP_VALUE,
            displayPriority = 7)
    public void setUseCompression(final boolean useCompression) {
        outputFactory.setUseCompression(useCompression);
    }

    @PipelineProperty(
            description = "Compression method to apply, if compression is enabled. Supported values: " +
                          CompressionUtil.SUPPORTED_COMPRESSORS + ".",
            defaultValue = DEFAULT_COMPRESSION_METHOD_PROP_VALUE,
            displayPriority = 8)
    public void setCompressionMethod(final String compressionMethod) {
        try {
            outputFactory.setCompressionMethod(compressionMethod);
        } catch (final RuntimeException e) {
            error(e.getMessage(), e);
            throw e;
        }
    }
}
