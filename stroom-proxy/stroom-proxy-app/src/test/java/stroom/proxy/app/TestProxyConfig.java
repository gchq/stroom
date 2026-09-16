/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.app;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.docref.DocRef;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.ForwardS3Config;
import stroom.proxy.app.handler.ForwarderConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.test.common.util.test.TestingHomeAndTempProvidersModule;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.config.ConfigValidator.Result;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;
import stroom.util.validation.ValidationModule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TestProxyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyConfig.class);

    private static final Set<Class<?>> WHITE_LISTED_CLASSES = Set.of(
            Logger.class,
            LambdaLogger.class,
            StroomDuration.class
    );

    @Test
    void testValidation(@TempDir final Path tempDir) throws IOException {

        final TestingHomeAndTempProvidersModule testingHomeAndTempProvidersModule =
                new TestingHomeAndTempProvidersModule(tempDir);

        final Injector injector = Guice.createInjector(
                testingHomeAndTempProvidersModule,
                new ValidationModule());

        final ProxyConfigValidator proxyConfigValidator = injector.getInstance(ProxyConfigValidator.class);

        final ProxyConfig vanillaAppConfig = new ProxyConfig();

        final ProxyPathConfig modifiedPathConfig = vanillaAppConfig.getPathConfig()
                .withHome(testingHomeAndTempProvidersModule.getHomeDir().toAbsolutePath().toString())
                .withTemp(tempDir.toAbsolutePath().toString());

        // A vanilla config is no longer valid on its own. receiptCheckMode defaults to
        // FEED_STATUS and downstreamHost.enabled to true, but hostname defaults to null - and a
        // receipt check that cannot reach its downstream admits everything. Supplying the hostname
        // here keeps this test's original subject ("the default config validates") while
        // testAReceiptCheckWithNoDownstreamHostnameIsRejected pins the new rule.
        final DownstreamHostConfig downstreamHostConfig = DownstreamHostConfig.copy(
                        vanillaAppConfig.getDownstreamHostConfig())
                .withHostname("downstream.example.com")
                .build();

        final ProxyConfig proxyConfig = AbstractConfigUtil.mutateTree(
                vanillaAppConfig,
                ProxyConfig.ROOT_PROPERTY_PATH,
                Map.of(ProxyConfig.ROOT_PROPERTY_PATH.merge(ProxyConfig.PROP_NAME_PATH), modifiedPathConfig,
                        ProxyConfig.ROOT_PROPERTY_PATH.merge(ProxyConfig.PROP_NAME_DOWNSTREAM_HOST),
                        downstreamHostConfig));

        // create the dirs so they validate ok
        Files.createDirectories(tempDir);
        Files.createDirectories(testingHomeAndTempProvidersModule.getHomeDir());
        Files.createDirectories(testingHomeAndTempProvidersModule.getHomeDir()
                .resolve(proxyConfig.getPathConfig().getData()));

        final Result<IsProxyConfig> result = proxyConfigValidator.validateRecursively(proxyConfig);

        result.handleViolations(ProxyConfigValidator::logConstraintViolation);

        Assertions.assertThat(result.hasErrorsOrWarnings())
                .isFalse();
    }

    /**
     * The shipped default is {@code receiptCheckMode: FEED_STATUS} with
     * {@code downstreamHost.enabled: true} and {@code hostname: null}. That combination passed
     * validation, built a hostless URI, failed every feed-status call, and the client then answered
     * Receive for every feed - a receipt policy that reports itself working while admitting
     * everything. It must now be a validation error.
     */
    @Test
    void testEveryKindOfForwardDestinationIsAForwarder() {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addForwardFileDestination(ForwardFileConfig.builder().enabled().withName("file").build())
                .addForwardHttpDestination(ForwardHttpPostConfig.builder().enabled(true).name("http").build())
                .addForwardS3Destination(new ForwardS3Config(
                        true, false, null, "s3", S3ClientConfig.builder().build(), null, null, null, null))
                .build();

        org.assertj.core.api.Assertions.assertThat(proxyConfig.streamAllForwarders().map(ForwarderConfig::getName))
                .as("the S3 list is a forwarder list like the other two")
                .containsExactlyInAnyOrder("file", "http", "s3");
        org.assertj.core.api.Assertions.assertThat(proxyConfig.streamAllEnabledForwarders().count()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(proxyConfig.getDirScannerConfig())
                .as("defaulted, like every other block")
                .isNotNull();
    }

    @Test
    void testAReceiptCheckWithNoDownstreamHostnameIsRejected() {
        final ProxyConfig proxyConfig = new ProxyConfig();

        // The default really is the vulnerable combination - if any of these change, this test is
        // asserting something other than it claims.
        Assertions.assertThat(proxyConfig.getReceiveDataConfig().getReceiptCheckMode())
                .isEqualTo(ReceiptCheckMode.FEED_STATUS);
        Assertions.assertThat(proxyConfig.getDownstreamHostConfig().isEnabled())
                .isTrue();
        Assertions.assertThat(proxyConfig.getDownstreamHostConfig().getHostname())
                .isNull();

        Assertions.assertThat(proxyConfig.isDownstreamHostnameValid())
                .as("a receipt check with no downstream hostname must not validate")
                .isFalse();
    }

    @Test
    void testAReceiptCheckWithADownstreamHostnameIsAccepted() {
        final ProxyConfig vanilla = new ProxyConfig();
        final ProxyConfig proxyConfig = AbstractConfigUtil.mutateTree(
                vanilla,
                ProxyConfig.ROOT_PROPERTY_PATH,
                Map.of(ProxyConfig.ROOT_PROPERTY_PATH.merge(ProxyConfig.PROP_NAME_DOWNSTREAM_HOST),
                        DownstreamHostConfig.copy(vanilla.getDownstreamHostConfig())
                                .withHostname("downstream.example.com")
                                .build()));

        Assertions.assertThat(proxyConfig.isDownstreamHostnameValid())
                .isTrue();
    }

    /**
     * A mode that does not consult the downstream needs no hostname.
     */
    @Test
    void testAModeThatDoesNotUseTheDownstreamNeedsNoHostname() {
        final ProxyConfig vanilla = new ProxyConfig();
        final ProxyConfig proxyConfig = AbstractConfigUtil.mutateTree(
                vanilla,
                ProxyConfig.ROOT_PROPERTY_PATH,
                Map.of(ProxyConfig.ROOT_PROPERTY_PATH.merge(ProxyConfig.PROP_NAME_RECEIVE),
                        ReceiveDataConfig.copy(vanilla.getReceiveDataConfig())
                                .withReceiptCheckMode(ReceiptCheckMode.RECEIVE_ALL)
                                .build()));

        Assertions.assertThat(proxyConfig.isDownstreamHostnameValid())
                .isTrue();
    }

    /**
     * Test to verify that all fields in the config tree of type stroom.*
     * implement IsProxyConfig . Also useful for seeing the object tree
     * and the annotations
     */
    @Test
    public void testIsProxyConfigUse() {
        checkProperties(ProxyConfig.class, "");
    }

    private void checkProperties(final Class<?> clazz, final String indent) {
        for (final Field field : clazz.getDeclaredFields()) {
            final Class<?> fieldClass = field.getType();

            // We are trying to inspect props that are themselves config objects
            if (fieldClass.getName().startsWith("stroom")
                    && fieldClass.getSimpleName().endsWith("Config")
                    && !WHITE_LISTED_CLASSES.contains(fieldClass)) {

                LOGGER.debug("{}Field {} : {} {}",
                        indent, field.getName(), fieldClass.getSimpleName(), fieldClass.getAnnotations());

                Assertions.assertThat(IsProxyConfig.class)
                        .withFailMessage(LogUtil.message("Class {} does not extend {}",
                                fieldClass.getName(),
                                IsProxyConfig.class.getName()))
                        .isAssignableFrom(fieldClass);

                // This field is another config object so recurs into it
                checkProperties(fieldClass, indent + "  ");
            } else {
                // Not a stroom config object so nothing to do
            }
        }
    }

    @Test
    void showPropsWithNullValues() {
        // list any config values that are null.  This may be valid so no assertions used.
        PropertyUtil.walkObjectTree(
                new ProxyConfig(),
                prop -> !prop.hasAnnotation(JsonIgnore.class),
                prop -> {
                    if (prop.getValueFromConfigObject() == null) {
                        LOGGER.warn("{} => {} is null",
                                prop.getParentObject().getClass().getSimpleName(),
                                prop.getName());
                    }
                });
    }

    @Test
    void showPropsWithCollectionValues() {
        // list any config values that are null.  This may be valid so no assertions used.
        PropertyUtil.walkObjectTree(
                new ProxyConfig(),
                prop -> true,
                prop -> {
                    final Class<?> valueClass = prop.getValueClass();
                    if (!valueClass.getName().startsWith("stroom")
                            && isCollectionClass(valueClass)) {
                        LOGGER.warn("{}.{} => {} => {}",
                                prop.getParentObject().getClass().getSimpleName(),
                                prop.getName(),
                                prop.getValueType(),
                                prop.getValueClass());
                    }
//                    if (prop.getValueType().getTypeName().matches("")) {
//                    }
                });
    }

    private boolean isCollectionClass(final Class<?> clazz) {
        return clazz.isAssignableFrom(List.class)
                || clazz.isAssignableFrom(Map.class)
                || clazz.equals(DocRef.class);
    }
}
