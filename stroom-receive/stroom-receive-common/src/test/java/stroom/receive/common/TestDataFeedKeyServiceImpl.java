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

package stroom.receive.common;

import stroom.cache.impl.CacheManagerImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.DataFeedKeyGenerator.KeyWithHash;
import stroom.security.api.UserIdentity;
import stroom.util.cache.CacheConfig;
import stroom.util.concurrent.ThreadUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestDataFeedKeyServiceImpl {

    private static final Set<DataFeedKeyHasher> DATA_FEED_KEY_HASHERS = Set.of(
            new Argon2DataFeedKeyHasher(),
            new BCryptDataFeedKeyHasher());

    @Mock
    private HttpServletRequest mockHttpServletRequest;


    @Test
    void authenticate_noKey() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                this::getReceiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        final AttributeMap attributeMap = new AttributeMap(Map.of(
        ));

        final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                mockHttpServletRequest,
                attributeMap);
        assertThat(optUserIdentity)
                .isEmpty();
    }

    @Test
    void authenticate_invalidKey1() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                this::getReceiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        setAuthHeaderOnMock("foo");
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, "1234"
        ));

        final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                mockHttpServletRequest,
                attributeMap);
        assertThat(optUserIdentity)
                .isEmpty();
    }

    @Test
    void authenticate_invalidKey2() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                this::getReceiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + "foo");
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, "1234"
        ));

        final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                mockHttpServletRequest,
                attributeMap);
        assertThat(optUserIdentity)
                .isEmpty();
    }

    @Test
    void authenticate_validButUnknownKey() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                this::getReceiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateFixedTestKey1();
        final HashedDataFeedKey hashedDataFeedKey = keyWithHash.hashedDataFeedKey();
        setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + keyWithHash.key());
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID,
                hashedDataFeedKey.getStreamMetaValue(StandardHeaderArguments.ACCOUNT_ID)
        ));

        // Don't load the key, so the service doesn't know about it
        Assertions.assertThatThrownBy(
                        () -> {
                            final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                                    mockHttpServletRequest,
                                    attributeMap);
                        })
                .isInstanceOf(StroomStreamException.class)
                .extracting(ex -> (StroomStreamException) ex)
                .extracting(StroomStreamException::getStroomStreamStatus)
                .extracting(StroomStreamStatus::getStroomStatusCode)
                .isEqualTo(StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED);
    }

    @Test
    void authenticate_validButExpiredKey() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                this::getReceiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        final Duration expiryDuration = Duration.ofSeconds(3);
        final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateRandomKey(
                "1234", Map.of(), Instant.now().plus(expiryDuration));
        final HashedDataFeedKey hashedDataFeedKey = keyWithHash.hashedDataFeedKey();
        setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + keyWithHash.key());
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID,
                hashedDataFeedKey.getStreamMetaValue(StandardHeaderArguments.ACCOUNT_ID)
        ));

        dataFeedKeyService.addDataFeedKeys(
                new HashedDataFeedKeys(List.of(hashedDataFeedKey)),
                Path.of("foo"));

        ThreadUtil.sleep(expiryDuration);

        // Don't load the key, so the service doesn't know about it
        Assertions.assertThatThrownBy(
                        () -> {
                            final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                                    mockHttpServletRequest,
                                    attributeMap);
                        })
                .isInstanceOf(StroomStreamException.class)
                .extracting(ex -> (StroomStreamException) ex)
                .extracting(StroomStreamException::getStroomStreamStatus)
                .extracting(StroomStreamStatus::getStroomStatusCode)
                .isEqualTo(StroomStatusCode.DATA_FEED_KEY_EXPIRED);
    }

    @Test
    void authenticate_validAndKnownKey() {
        final ReceiveDataConfig receiveDataConfig = getReceiveDataConfig();
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                () -> receiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateFixedTestKey1();
        final HashedDataFeedKey hashedDataFeedKey = keyWithHash.hashedDataFeedKey();
        setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + keyWithHash.key());
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID,
                hashedDataFeedKey.getStreamMetaValue(StandardHeaderArguments.ACCOUNT_ID)
        ));

        dataFeedKeyService.addDataFeedKeys(
                new HashedDataFeedKeys(List.of(hashedDataFeedKey)),
                Path.of("foo"));

        final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                mockHttpServletRequest,
                attributeMap);

        final UserIdentity userIdentity = optUserIdentity.get();
        assertThat(userIdentity.subjectId())
                .isEqualTo(DataFeedKeyUserIdentity.SUBJECT_ID_PREFIX
                           + hashedDataFeedKey.getStreamMetaValue(StandardHeaderArguments.ACCOUNT_ID));
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(userIdentity.subjectId());
    }

    @Test
    void authenticate_multipleValidKnownKeys() {
        final ReceiveDataConfig receiveDataConfig = getReceiveDataConfig();
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                () -> receiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        final List<KeyWithHash> keys = new ArrayList<>(15);
        final Instant time = Instant.now();
        for (int accId = 1; accId <= 3; accId++) {
            for (int i = 0; i < 5; i++) {
                final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateRandomKey(
                        String.valueOf(accId),
                        Map.of(),
                        time.plus(i + 1, ChronoUnit.MINUTES));
                keys.add(keyWithHash);
            }
        }
        final List<HashedDataFeedKey> hashedDataFeedKeys = keys.stream()
                .map(KeyWithHash::hashedDataFeedKey)
                .toList();
        dataFeedKeyService.addDataFeedKeys(new HashedDataFeedKeys(hashedDataFeedKeys), Path.of("foo"));

        for (final KeyWithHash key : keys) {
            final String plainKey = key.key();
            final HashedDataFeedKey hashedDataFeedKey = key.hashedDataFeedKey();
            final String accId = hashedDataFeedKey.getStreamMetaValue(StandardHeaderArguments.ACCOUNT_ID);

            setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + plainKey);
            final AttributeMap attributeMap = hashedDataFeedKey.getAttributeMap();

            final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                    mockHttpServletRequest,
                    attributeMap);

            final UserIdentity userIdentity = optUserIdentity.get();
            assertThat(userIdentity.subjectId())
                    .isEqualTo(DataFeedKeyUserIdentity.SUBJECT_ID_PREFIX
                               + accId);
            assertThat(userIdentity.getDisplayName())
                    .isEqualTo(userIdentity.subjectId());
        }
    }

    @Test
    void authenticate_duplicateValidKnownKeys() {
        final ReceiveDataConfig receiveDataConfig = getReceiveDataConfig();
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                () -> receiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        final List<KeyWithHash> keys = new ArrayList<>(5);
        final List<KeyWithHash> keysToUse = new ArrayList<>(5);
        final Instant time = Instant.now();
        final int accId = 1;
        // Five unique keys, each duplicated 3 times
        for (int i = 0; i < 5; i++) {
            final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateRandomKey(
                    String.valueOf(accId),
                    Map.of(),
                    time.plus(i + 1, ChronoUnit.MINUTES));
            for (int j = 0; j < 3; j++) {
                keys.add(keyWithHash);
            }
            keysToUse.add(keyWithHash);
        }
        final List<HashedDataFeedKey> hashedDataFeedKeys = keys.stream()
                .map(KeyWithHash::hashedDataFeedKey)
                .toList();
        dataFeedKeyService.addDataFeedKeys(new HashedDataFeedKeys(hashedDataFeedKeys), Path.of("foo"));

        // Test each key twice to get a cache hit on 2nd go
        for (int i = 0; i < 2; i++) {
            for (final KeyWithHash key : keysToUse) {
                final String plainKey = key.key();
                final HashedDataFeedKey hashedDataFeedKey = key.hashedDataFeedKey();
                final String accId2 = hashedDataFeedKey.getStreamMetaValue(StandardHeaderArguments.ACCOUNT_ID);

                setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + plainKey);
                final AttributeMap attributeMap = hashedDataFeedKey.getAttributeMap();

                final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                        mockHttpServletRequest,
                        attributeMap);

                final UserIdentity userIdentity = optUserIdentity.get();
                assertThat(userIdentity.subjectId())
                        .isEqualTo(DataFeedKeyUserIdentity.SUBJECT_ID_PREFIX
                                   + accId2);
                assertThat(userIdentity.getDisplayName())
                        .isEqualTo(userIdentity.subjectId());
            }
        }
    }

    @Test
    void authenticate_badAccountId() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                this::getReceiveDataConfig,
                DATA_FEED_KEY_HASHERS,
                new CacheManagerImpl());
        final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateFixedTestKey1();
        final HashedDataFeedKey hashedDataFeedKey = keyWithHash.hashedDataFeedKey();
        setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + keyWithHash.key());
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, "foo"));

        dataFeedKeyService.addDataFeedKeys(
                new HashedDataFeedKeys(List.of(hashedDataFeedKey)),
                Path.of("foo"));

        Assertions.assertThatThrownBy(
                        () -> {
                            dataFeedKeyService.authenticate(mockHttpServletRequest, attributeMap);
                        })
                .isInstanceOf(StroomStreamException.class)
                .extracting(e ->
                        ((StroomStreamException) e).getStroomStreamStatus().getStroomStatusCode())
                .isEqualTo(StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED);
    }

    private void setAuthHeaderOnMock(final String value) {
        Mockito.when(mockHttpServletRequest.getHeader(DataFeedKeyServiceImpl.AUTHORIZATION_HEADER))
                .thenReturn(value);
    }

    private ReceiveDataConfig getReceiveDataConfig() {
        // Use a vanilla CacheConfig so it doesn't user dropwizard metrics
        return ReceiveDataConfig.builder()
                .withAuthenticatedDataFeedKeyCache(new CacheConfig())
                .build();
    }
}
