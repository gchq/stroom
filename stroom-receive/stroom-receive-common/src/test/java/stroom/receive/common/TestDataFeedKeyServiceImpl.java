package stroom.receive.common;

import stroom.cache.impl.CacheManagerImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.DataFeedKeyGenerator.KeyWithHash;
import stroom.security.api.UserIdentity;
import stroom.test.common.MockMetrics;

import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
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
                ReceiveDataConfig::new,
                new CacheManagerImpl(new MockMetrics()));
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
                ReceiveDataConfig::new,
                new CacheManagerImpl(new MockMetrics()));
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
                ReceiveDataConfig::new,
                new CacheManagerImpl(new MockMetrics()));
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
                ReceiveDataConfig::new,
                new CacheManagerImpl(new MockMetrics()));
        final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateFixedTestKey1();
        final HashedDataFeedKey hashedDataFeedKey = keyWithHash.hashedDataFeedKey();
        setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + keyWithHash.key());
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, hashedDataFeedKey.getAccountId()
        ));

        final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                mockHttpServletRequest,
                attributeMap);
        assertThat(optUserIdentity)
                .isEmpty();
    }

    @Test
    void authenticate_validAndKnownKey() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                ReceiveDataConfig::new,
                new CacheManagerImpl(new MockMetrics()));
        final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateFixedTestKey1();
        final HashedDataFeedKey hashedDataFeedKey = keyWithHash.hashedDataFeedKey();
        setAuthHeaderOnMock(DataFeedKeyServiceImpl.BEARER_PREFIX + keyWithHash.key());
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, hashedDataFeedKey.getAccountId()
        ));

        dataFeedKeyService.addDataFeedKeys(
                new HashedDataFeedKeys(List.of(hashedDataFeedKey)),
                Path.of("foo"));

        final Optional<UserIdentity> optUserIdentity = dataFeedKeyService.authenticate(
                mockHttpServletRequest,
                attributeMap);

        final UserIdentity userIdentity = optUserIdentity.get();
        assertThat(userIdentity.getSubjectId())
                .isEqualTo(hashedDataFeedKey.getSubjectId());
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(hashedDataFeedKey.getDisplayName());
    }

    @Test
    void authenticate_badAccountId() {
        final DataFeedKeyServiceImpl dataFeedKeyService = new DataFeedKeyServiceImpl(
                ReceiveDataConfig::new,
                new CacheManagerImpl(new MockMetrics()));
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
                .isEqualTo(StroomStatusCode.INVALID_ACCOUNT_ID);
    }

    private void setAuthHeaderOnMock(final String value) {
        Mockito.when(mockHttpServletRequest.getHeader(DataFeedKeyServiceImpl.AUTHORIZATION_HEADER))
                .thenReturn(value);
    }
}
