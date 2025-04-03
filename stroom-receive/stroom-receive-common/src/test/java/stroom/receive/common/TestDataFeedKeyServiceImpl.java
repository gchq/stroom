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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestDataFeedKeyServiceImpl {

    @Mock
    private HttpServletRequest mockHttpServletRequest;

    @Test
    void authenticate_noKey() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                this::getReceiveDataConfig,
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
                new CacheManagerImpl());
        setAuthHeaderOnMock("foo");
        final AttributeMap attributeMap = new AttributeMap(Map.of(
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
                new CacheManagerImpl());
        setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + "foo");
        final AttributeMap attributeMap = new AttributeMap(Map.of(
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
        assertThat(userIdentity.getSubjectId())
                .isEqualTo(DataFeedKeyUserIdentity.SUBJECT_ID_PREFIX
                           + hashedDataFeedKey.getStreamMetaValue(StandardHeaderArguments.ACCOUNT_ID));
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(userIdentity.getSubjectId());
    }

    @Test
    void authenticate_badAccountId() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                this::getReceiveDataConfig,
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
