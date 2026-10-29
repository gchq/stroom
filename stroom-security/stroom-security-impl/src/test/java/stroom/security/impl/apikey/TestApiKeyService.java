/*
 * Copyright 2023 Crown Copyright
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

package stroom.security.impl.apikey;

import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.ApiKeyGenerator;
import stroom.security.common.impl.hash.HashFunctionFactoryImpl;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.AuthorisationConfig;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.UserCache;
import stroom.security.impl.UserDao;
import stroom.security.impl.apikey.ApiKeyService.DuplicateApiKeyException;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.CreateHashedApiKeyResponse;
import stroom.security.shared.HashAlgorithm;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.User;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.test.common.TestUtil;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.json.JsonUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestApiKeyService.class);

    @SuppressWarnings("checkstyle:LineLength")
    private static final String TEST_DATA = """
            [ {
              "inputText" : "sak_81a7423a2c_Thfc29kDsawB74CGKLfFpaGdHD8PxFFKNn8cuCeSwZQkb9VHtLVkrnD4VSLsVCuVhFxv5BGrCK6GvqbeVp3ChhSbwj8eWBFwDFePMcoWLBDRYv5F4tGSrhVPa2TMsMD9",
              "hash" : "AsQ3BehMa7k8oBvmB4QxpugU16a7bEznHPfqKpyW96ok",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_ace86b8743_Hryzh5zA81CcqkMjht6UQvxkbcD8BgZBYx14AyWGoGxZUkGDngBJMGL7D3WgJ4qnMU6Mcu5CBJUueEwWXNrDKs1tzxgwK4DS2q2v5n6exkwNLTF8rBf1aSVjhvgEcK5G",
              "hash" : "77xmimJsSwUR7qxJDZvb2w1QzrgDQHUV4mCT26CveckY",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_be65214a11_KMZJ8zRhWMbU5BtS3rfvutAQqxQr3HB12mczyjbBN6uJh6Ge8eiCsteQmW28577u9PZDEEcYumNWCPoZDtdNKmgfhVYQkBwBeT3Z3b8ADWb8EdKFjsWF3TJxihj7rVDs",
              "hash" : "CmSpBrFbeX4u8jNyKxjmDBMFCB5U1n9HJk47fVNd2m9Z",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_6c2570ba5a_eiLsfzBZVrMvfPadYQ4kdSaUoyC8ZwifhZrfQRiwh4JdKygyCDp7JBG2DdUYaY4LdyoaghTrhuZsDxUJ6gefi9KJf6e5AvXr9MvcEFtehAihju4LNavC8f6vTBabUifv",
              "hash" : "4C8dfyRTW5HA5qLnMzdkQyfwHfW9HuekJzq6Aeyg1Poi",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_70affe60a9_8N6bocei5Axr3vXFoJF5MwwXvmQDs1B67cdRPBt7LP3D5xxxHbZ3T5GJDTg3rjaZDo4K1swbGuEpdJ9un9zGYByFNDo8thyjeLDqvB24rHiHNKxAYkQG6dKEkpBtE16s",
              "hash" : "H7N2S71iNJmagnUyZFVugeN7LamQQbX9W6rWg9cfZHbJ",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_89a95eea7e_Sx7Mp96NEeUoxSWnn9XpksDQiNGMXLoskiGMVp2bSHDSkf2fxfrxFCfAyKzi4ZSGHqB6LoGBf9ccP7LRmdXLQKgcJYPVAfPcgGHBmojD4a4P8gyeVuYdkR8VnhSh872j",
              "hash" : "Dw8bviqg6W6WR3rFq4LvA4DjcJVvZghZyy741EZL2ahD",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_056092233a_1wUhbWETbshh1RKPVS55UVLgDwM8UofjC2XA7fjiuA818usMyWkeP8sZF1yYsm33ke5Y8Hkn4VmKTmUxSJ7fxG8exXCLpWP2YCvaDwp2hWWos9Uw5Bdgf1yP5HpGvfXL",
              "hash" : "5qD2JW5jyTmH6ADuFyrYmfvccVAorPjGkuhpFiSQHi3H",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_cd33b6bcfe_K5q7NTRRx4CNWdLVnjbUb9um1cUf1LgNrpqURTYjmpKnAvYpFvNy4pnE4w6C2iiAtMAwtxuqETGfETVn3wyqARPomYND5wPVu6GDDynz6Gk6cXgAP68N9fUAn87kXtuy",
              "hash" : "H3X15AUovfiSBQSwThMHhWoY3RbBH9H8k7i9XTJVQwUJ",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_9c0c19e4f3_iMYUTrgndZKsfgVcdtjGxRv1Tnsfhs5eBZgrU5yVDvRxNspMUyYUt77QNvDpdbxNsq65BT6GS4rynQbwqRJbLbcLLkPLBZKRsMDHfJingXKtwGRJuWgPnRX5rqCVN36V",
              "hash" : "CubLMHRx9bgDr9A3ahCDmGYwED5Dm5ErvQYWmfsieSLD",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_b9f87c215c_6XRzikiVTKe1zNc1PKBe1pWwJ3bNiHzh3DzL8yHqrmZnTk6EEZofiNFzssXpuY289KkAr4WiW4fo1CmbSSsgT7URBKfyVbproSYzg2PqKWnEYtpw7eeBURiGbbxJxQrx",
              "hash" : "B9phJrZR2sebpP8cpCGyb97pmxrv3aEcx5PcYcM9P6Fq",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_fac2b43509_XmUEtkjRSPeRNeC1L9JjBRcCBN2TGCGre4WFDchX7AxGVAFv4Cm1kTHuzLgHFsZzwoFvznXQ2NsYFwjutHMMU5zEJSxUyHxK4ygXA1DkLdVQLUqiVutuxuvn2pJNcMGt",
              "hash" : "$2a$10$prGGI/q95cCFYK5ppfk5CeBOyTH179w0.l1pfXQax97TR8ICQJIqe",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_8cbc0e7da3_9x3smvNT9ecu4W2Eyd8n6uiDGVDS6rbDSZNUriBwyqmS8Pu4FRxnUGxiQBVAfTdufYCr5HYiTZTtQsg4Eh7zruMzZY5CbiLrKTfkdmWUuKsJ8Rx2QrZMNMAGnry6zSvB",
              "hash" : "$2a$10$ZwiXUpJIiN0SQENIuQ04NOFaIWOd22RMGFUQ3BDHEPlYG6octsv7.",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_0de20468fb_9pNR2K4aMRWJ5gNj2ZxbShEhhzdDR56TomqFL4MRVC4WXUZrYWUGTn1iCphiJAaL3DCGvFyFGw1yK6zSiESdXzA6dv5fxUqDXdp5EMQXyugAt54GKxLMCDdbQzku95XD",
              "hash" : "$2a$10$l55kBOGn2UTb1NTBQYVoyuW78WA4rswioZNW7YOKUW31FWshFUteq",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_f2270f2e46_QuAECHvWo6AJEjYmRPKMjfV7WZU5Jai3uScTyLKKGUdha1pjGGkLVKbkqymCE8Y81CKzkmfH2MVzEitB5y4y7Bw1KpXYPsu31pwUbNqjiAnsPG7MATEEyKPUnvwhDzai",
              "hash" : "$2a$10$Bq1/peABFEaCoSWSGcE6NO5KBGw5rZrS.9qRjP9AhICV/78gmsNJm",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_7f0c3afb78_XfmfRRyiGefSzVXaGr9LteWQbh81VDFiyte41zbcy9CLEMnUSLJP524Eq2LmrttzGrrv61jGzwuv9Wcf4UijLd66NCLQpCiiWaFEF96w4hfDMDpTLXv4AkcAqGRcGQvX",
              "hash" : "$2a$10$Y/gNpZ92jMvND9VGMW5iF.A4qxvkM1WmEn7uWr5.KOD.8hTx5xD86",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_15e0fa755a_YGj3as2f6ouxJLakxJR4GAVi4CXvdpapayJZxd2a2mcTcYpZfRDHd1JNm1v4qSUur7Swqenh2QUPdx76s28YecqCE8rJqNq8uSRQmBgEzEu6zRr7EKP1FzdLdqvsBnFb",
              "hash" : "9hXPo4gjQx2AfghkEZ6ZiLYtFiJmyvQ7v12jFzt1cMdXLoAJbGvQdaaRVXsrAE1aRZ",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_d068c1ece9_e1XELvtyCwqpZjGpR3Kbq38ewmKhz6BVPNWhpHPLd63vmiuxfwqfxwkUHjhrZNJNGwMWx6hqQ5VrizHTKSFuai67ouASKzz3qEjjmSbe6HuabExTqK49CYNEFkvco1CK",
              "hash" : "4SBcLG1owpQZNeWHi1NtihLiEJTxJvWMHMaWxDyxj91Bn7R6oMAQSjYf5XUcAXX9Cq",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_ece1c32b0f_b8stRond8xoCLHB2mxkeeFpizjB69AMagJYWXapCknfd9yJE3psEcfAHCCM54PrB1HpqDUNmWLS9VnrR9auaJpWkjgaAaeNAvegfxvd3s4QLAF3HFw4jFJQmxetHJFet",
              "hash" : "5g8u5yym75bP37YVpMZnUrxNh7EFJ6Fa339T3nSxRXfEd6GhByR91VbrNRZDwpQnW3",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_08b23ad75f_w21NEEuNyeudhrAYAYm9oP8zyWpdwyVRvEypdZPgJoYb3BfVFwNaAGzqUUkLbKYmF9RkesdZNC4WRGzdBacDAkprsTDBLC6Jk2dmGJEWtumQ238679LCZWZby1RCzgbk",
              "hash" : "9dB6gijE2JKRttLePt8vJyXGeRQ7cMnjFg4rLMJzRA5iyJPhG7yaZiqReyGwUg238z",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_fff5ca59bd_EJ7TtEpWM3Atin2NpZgtwD9nNwT8cuThMCxq3X7YbXckYnjEzhAkCVoKZxkGrLywqNY48eRh4UcRgFaipxjUJ8YJ5jzdEFgdnPfYmNaUgp6jSgFqVUbAN2H8X1vXp6tg",
              "hash" : "4GPWW3m1SEa6MyS11m42ceaADSw3NDWFSC4jRxyNRGoGC7V4qnusFxoNqJpeC53AZm",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_12b257d76c_zfb1gBo8etT7Ebr6ATpduRBhMkPVyD7cK5NNhMW4NoHAwAjdBtkon4Yu5QCBwU9fEdTqoBwHPkQsJ37nPLrabq4izgyNXquz4jBnvzpA2orRsdToM11xJoDzFeqtp8zc",
              "hash" : "3bPQSXVTZTFkxbTnErWJ9M28c6KdkuMejzeFiTrdJ81gx2QWD8ArALrHRrh3ZjoxyHsviqXvnaVFE5zvxy7nohWB",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_0f7d5c1b27_dHkNdSBqs8xdHhn9ruLwmytwFsi1pTnYT5jSMqdRmstqHHSTYjgAHx7qqmCa2Y1oo47VrZMQtWaMxMvaL9CJRoS1P7if7YQygLPnqyPWzVixnPbBb9ZKmMm7yxpKo8gD",
              "hash" : "4HqdqibDvfNk1TcQJT3Vig3p5cRvUpGwtCivMuJzPH3fsG4TqVjyHGyDk4tgHAqMCRHatgf98a9zczhZCfXmV4F6",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_c373897698_3CGd7PQd7WLFVh1U45hB3vVhhb4sHMjQsfAkRfC9mR5saynXzuu28Qj5sxERS6pTL6Q8nrDCniP25uuzux6uuVY6HoPhnPt5Dn8wv5JdKMFA2pj14gTZMKEy5gFaBtjw",
              "hash" : "3EpAt5xd9mYV1ZGvJmfEaDetUz4gKhCZq1wFVqgDmUAD8tZ5UJafYP52LR9WL7xgb2LhpEixBnMfBgvVbbdtD21o",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_1701aafbec_Xg1QMMWAAqFi7pcq4kdHPKsmaTM48M3CmsJCbG7cBrtJruGw4cNuYsmDuZmQa8sTYHuvMKhSxAcaoT6bxNnRma5vsounA3STgaM9sL76HrLtCeHtZJh4ohtzz4FJ6CzR",
              "hash" : "5jp25Fs8JozPYUkYhqa94gwvQ8vKKji1WnqoXcXstwALybx6HF5H1noRrzgzLxr49zYMksf3bWWeYKrY9MPrxTwu",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_227b6d7f41_EXveb9Gnif3dFECyzy885PAddWTA8Yuix4VJiVyJPFzBDwnCrLnCCEzUGgdQdy1aJL2vE2iFHLTikw4ydPsPTFrWFcfN2iTBvjHfi4faWMFZRDCw5HLeb7rButW5EGSC",
              "hash" : "ZnyHRbwknVvqVZNc7L5NSq9sDZhew1wNipFxHmcg8WAM9o8vUbucu3TeGaXSNhb7sFb2hfZJdXUKeYUhZ2sFQ2g",
              "hashAlgorithm" : "SHA2_512"
            } ]""";

    private final SecurityContext securityContext = new MockSecurityContext();
    private final ApiKeyGenerator apiKeyGenerator = new ApiKeyGenerator();

    @Mock
    private ApiKeyDao mockApiKeyDao;
    @Mock
    private UserDao mockUserDao;
    @Mock
    private EntityEventBus mockEntityEventBus;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        final CacheManager cacheManager = new CacheManagerImpl();
        final UserCache userCache = new UserCache(cacheManager, AuthorisationConfig::new, () -> mockUserDao);
        apiKeyService = new ApiKeyService(
                mockApiKeyDao,
                securityContext,
                apiKeyGenerator,
                new CacheManagerImpl(),
                AuthenticationConfig::new,
                userCache,
                mockEntityEventBus,
                new ApiKeyHasherFactoryImpl(new HashFunctionFactoryImpl()));
    }

    @Test
    void verifyApiKey_validKey_returnsUserDescription() {
        final String apiKey = apiKeyGenerator.generateRandomApiKey();
        final User owner = createUser("mySubjectId");
        final String apiKeyHash = apiKeyService.computeApiKeyHash(apiKey);

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(List.of(HashedApiKey.builder()
                        .owner(owner.asRef())
                        .apiKeyHash(apiKeyHash)
                        .apiKeyPrefix(ApiKeyGenerator.extractPrefixPart(apiKey))
                        .enabled(true)
                        .build()));
        Mockito.when(mockUserDao.getByUuid(owner.getUuid()))
                .thenReturn(Optional.of(owner));

        final Optional<UserDesc> result = apiKeyService.verifyApiKey(new VerifyApiKeyRequest(apiKey));

        assertThat(result)
                .contains(new UserDesc(owner.getSubjectId(), owner.getDisplayName(), owner.getFullName()));
    }

    @Test
    void verifyApiKey_invalidKey_returnsEmpty() {
        final VerifyApiKeyRequest request = new VerifyApiKeyRequest("not-an-api-key");

        final Optional<UserDesc> result = apiKeyService.verifyApiKey(request);

        assertThat(result)
                .isEmpty();
        Mockito.verifyNoInteractions(mockApiKeyDao, mockUserDao);
    }

    @Test
    void verifyApiKey_requiredPermissionMissing_returnsEmpty() {
        final SecurityContext mockSecurityContext = Mockito.spy(new MockSecurityContext());
        Mockito.doReturn(false)
                .when(mockSecurityContext)
                .hasAppPermissions(Mockito.any(UserIdentity.class), Mockito.any(AppPermissionSet.class));

        final CacheManager cacheManager = new CacheManagerImpl();
        final UserCache userCache = new UserCache(cacheManager, AuthorisationConfig::new, () -> mockUserDao);
        apiKeyService = new ApiKeyService(
                mockApiKeyDao,
                mockSecurityContext,
                apiKeyGenerator,
                new CacheManagerImpl(),
                AuthenticationConfig::new,
                userCache,
                mockEntityEventBus,
                new ApiKeyHasherFactoryImpl(new HashFunctionFactoryImpl()));

        final String apiKey = apiKeyGenerator.generateRandomApiKey();
        final User owner = createUser("mySubjectId");
        final String apiKeyHash = apiKeyService.computeApiKeyHash(apiKey);
        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(List.of(HashedApiKey.builder()
                        .owner(owner.asRef())
                        .apiKeyHash(apiKeyHash)
                        .apiKeyPrefix(ApiKeyGenerator.extractPrefixPart(apiKey))
                        .enabled(true)
                        .build()));
        Mockito.when(mockUserDao.getByUuid(owner.getUuid()))
                .thenReturn(Optional.of(owner));

        final VerifyApiKeyRequest request = new VerifyApiKeyRequest(
                apiKey,
                AppPermissionSet.of(AppPermission.MANAGE_API_KEYS));

        assertThat(apiKeyService.verifyApiKey(request))
                .isEmpty();
    }

    @Test
    void create_success() throws DuplicateApiKeyException {
        final UserRef owner = UserRef.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        final CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli(),
                "key1",
                "some comments",
                true,
                HashAlgorithm.SHA3_256);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        final AtomicReference<String> hashRef = new AtomicReference<>();
        Mockito.doAnswer(
                        invocation -> {
                            final CreateHashedApiKeyRequest request2 = invocation.getArgument(0);
                            final HashedApiKeyParts parts = invocation.getArgument(1);
                            hashRef.set(parts.apiKeyHash());
                            return hashedApiKey;
                        })
                .when(mockApiKeyDao)
                .create(Mockito.any(), Mockito.any());

        final CreateHashedApiKeyResponse response = apiKeyService.create(request);

        assertThat(response.getHashedApiKey())
                .isEqualTo(hashedApiKey);

        final String apiKey = response.getApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKey);

        // Make sure hash passed to dao matched the hash of the returned key
        assertThat(hash)
                .isEqualTo(hashRef.get());
    }

    @Test
    void create_noExpireTime() throws DuplicateApiKeyException {
        final UserRef owner = UserRef.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        final CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                null,
                "key1",
                "some comments",
                true,
                HashAlgorithm.SHA3_256);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();


        final AtomicReference<String> hashRef = new AtomicReference<>();
        final AtomicReference<Long> expireTimeRef = new AtomicReference<>();
        Mockito.doAnswer(
                        invocation -> {
                            final CreateHashedApiKeyRequest request2 = invocation.getArgument(0);
                            final HashedApiKeyParts parts = invocation.getArgument(1);
                            hashRef.set(parts.apiKeyHash());
                            expireTimeRef.set(request2.getExpireTimeMs());
                            return hashedApiKey;
                        })
                .when(mockApiKeyDao)
                .create(Mockito.any(), Mockito.any());

        final CreateHashedApiKeyResponse response = apiKeyService.create(request);

        assertThat(response.getHashedApiKey())
                .isEqualTo(hashedApiKey);
        final Duration maxExpireAge = new AuthenticationConfig().getMaxApiKeyExpiryAge().getDuration();
        assertThat(expireTimeRef.get())
                .isCloseTo(Instant.now().plus(maxExpireAge).toEpochMilli(), Percentage.withPercentage(5));

        final String apiKey = response.getApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKey);

        // Make sure hash passed to dao matched the hash of the returned key
        assertThat(hash)
                .isEqualTo(hashRef.get());
    }

    @Test
    void create_expireTimeTooBig() throws DuplicateApiKeyException {
        final UserRef owner = UserRef.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();
        final Instant now = Instant.now();
        final Duration maxExpireAge = new AuthenticationConfig().getMaxApiKeyExpiryAge().getDuration();
        final Instant maxExpireTime = now.plus(maxExpireAge);
        // Over the limit
        final Instant expireTime = maxExpireTime.plus(Duration.ofDays(10));

        final CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                expireTime.toEpochMilli(),
                "key1",
                "some comments",
                true,
                HashAlgorithm.SHA3_256);

        Assertions.assertThatThrownBy(() ->
                        apiKeyService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("is after");
    }

    @Test
    void create_hashClash() throws DuplicateApiKeyException {
        final UserRef owner = UserRef.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        final CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli(),
                "key1",
                "some comments",
                true,
                HashAlgorithm.SHA3_256);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        final AtomicReference<String> hashRef = new AtomicReference<>();
        final AtomicInteger iteration = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            if (iteration.incrementAndGet() <= 3) {
                                throw new DuplicateApiKeyException("dup hash", new RuntimeException("foo"));
                            }

                            final CreateHashedApiKeyRequest request2 = invocation.getArgument(0);
                            final HashedApiKeyParts parts = invocation.getArgument(1);
                            hashRef.set(parts.apiKeyHash());
                            return hashedApiKey;
                        })
                .when(mockApiKeyDao)
                .create(Mockito.any(), Mockito.any());

        final CreateHashedApiKeyResponse response = apiKeyService.create(request);

        assertThat(response.getHashedApiKey())
                .isEqualTo(hashedApiKey);

        final String apiKey = response.getApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKey);

        // Make sure hash passed to dao matched the hash of the returned key
        assertThat(hash)
                .isEqualTo(hashRef.get());
    }

    @Test
    void create_prefixClash() throws DuplicateApiKeyException {
        final UserRef owner = UserRef.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        final String name = "key-" + UUID.randomUUID().toString();
        final CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli(),
                name,
                "some comments",
                true,
                HashAlgorithm.SHA3_256);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        final AtomicReference<String> hashRef = new AtomicReference<>();
        final AtomicInteger iteration = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            if (iteration.incrementAndGet() <= 3) {
                                throw new DuplicateApiKeyException("dup prefix", new RuntimeException("foo"));
                            }

                            final CreateHashedApiKeyRequest request2 = invocation.getArgument(0);
                            final HashedApiKeyParts parts = invocation.getArgument(1);
                            hashRef.set(parts.apiKeyHash());
                            return hashedApiKey;
                        })
                .when(mockApiKeyDao)
                .create(Mockito.any(), Mockito.any());

        final CreateHashedApiKeyResponse response = apiKeyService.create(request);

        assertThat(response.getHashedApiKey())
                .isEqualTo(hashedApiKey);

        final String apiKey = response.getApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKey);

        // Make sure hash passed to dao matched the hash of the returned key
        assertThat(hash)
                .isEqualTo(hashRef.get());
    }

    @Test
    void fetchVerifiedIdentityFromRequest_success() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);

        final User owner = User.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();
        final UserRef ownerRef = owner.asRef();

        final List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .owner(ownerRef)
                        .apiKeyHash(hash)
                        .build());

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);
        Mockito.when(mockUserDao.getByUuid(Mockito.anyString()))
                .thenReturn(Optional.of(owner));
        Mockito.when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(apiKeyStr);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(mockRequest);

        assertThat(opUserIdentity)
                .isNotEmpty();
        final UserIdentity userIdentity = opUserIdentity.get();
        assertThat(userIdentity.subjectId())
                .isEqualTo(owner.getSubjectId());
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(owner.getDisplayName());
    }

    @Test
    void fetchVerifiedIdentityFromRequest_notApiKey() {
        final String badApiKeyStr = "XXX_" + apiKeyGenerator.generateRandomApiKey();
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);

        // Return something that is not an API
        Mockito.when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(badApiKeyStr);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(mockRequest);

        assertThat(opUserIdentity)
                .isEmpty();
    }

    private User createUser(final String subjectId) {
        return User.builder()
                .uuid(subjectId + "_uuid")
                .subjectId(subjectId)
                .displayName(subjectId + "_displayName")
                .build();
    }

    @Test
    void fetchVerifiedIdentity_success_oneKey() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final User owner = createUser("mySubjectId");
        final UserRef ownerRef = owner.asRef();

        final List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .owner(ownerRef)
                        .apiKeyHash(hash)
                        .build());

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);
        Mockito.when(mockUserDao.getByUuid(Mockito.anyString()))
                .thenReturn(Optional.of(owner));

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr);

        assertThat(opUserIdentity)
                .isNotEmpty();
        final UserIdentity userIdentity = opUserIdentity.get();
        assertThat(userIdentity.subjectId())
                .isEqualTo(owner.getSubjectId());
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(owner.getDisplayName());
    }

    @Test
    void fetchVerifiedIdentity_success_multipleKeys() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final User owner1 = createUser("mySubjectId1");
        final User owner2 = createUser("mySubjectId2");
        final User owner3 = createUser("mySubjectId3");

        final List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .owner(owner1.asRef())
                        .apiKeyHash("another hash")
                        .hashAlgorithm(HashAlgorithm.BCRYPT)
                        .build(),
                HashedApiKey.builder()
                        .owner(owner2.asRef())
                        .apiKeyHash("and another hash")
                        .hashAlgorithm(HashAlgorithm.ARGON_2)
                        .build(),
                HashedApiKey.builder()
                        .owner(owner3.asRef())
                        .apiKeyHash(hash)
                        .hashAlgorithm(HashAlgorithm.SHA3_256)
                        .build());

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);
        Mockito.when(mockUserDao.getByUuid(Mockito.eq(owner3.getUuid())))
                .thenReturn(Optional.of(owner3));

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr);

        assertThat(opUserIdentity)
                .isNotEmpty();
        final UserIdentity userIdentity = opUserIdentity.get();
        assertThat(userIdentity.subjectId())
                .isEqualTo(owner3.getSubjectId());
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(owner3.getDisplayName());
    }

    @Test
    void fetchVerifiedIdentity_noValid_empty() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final List<HashedApiKey> apiKeys = Collections.emptyList();

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

        Assertions.assertThatThrownBy(() ->
                        apiKeyService.fetchVerifiedIdentity(apiKeyStr))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void fetchVerifiedIdentity_noHashMatch_multipleKeys() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String prefix = ApiKeyGenerator.extractPrefixPart(apiKeyStr);
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final UserRef owner1 = UserRef.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();
        final UserRef owner2 = UserRef.builder()
                .uuid("myUuid2")
                .subjectId("mySubjectId2")
                .displayName("myDisplayName2")
                .build();
        final UserRef owner3 = UserRef.builder()
                .uuid("myUuid3")
                .subjectId("mySubjectId3")
                .displayName("myDisplayName3")
                .build();

        final List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .owner(owner1)
                        .apiKeyHash("another hash")
                        .apiKeyPrefix(prefix)
                        .hashAlgorithm(HashAlgorithm.BCRYPT)
                        .expireTimeMs(System.currentTimeMillis() + 1000000)
                        .enabled(true)
                        .build(),
                HashedApiKey.builder()
                        .owner(owner2)
                        .apiKeyHash("and another hash")
                        .apiKeyPrefix(prefix)
                        .hashAlgorithm(HashAlgorithm.ARGON_2)
                        .expireTimeMs(System.currentTimeMillis() + 1000000)
                        .enabled(true)
                        .build(),
                HashedApiKey.builder()
                        .owner(owner3)
                        .apiKeyHash("and yet another hash")
                        .apiKeyPrefix(prefix)
                        .hashAlgorithm(HashAlgorithm.SHA3_256)
                        .expireTimeMs(System.currentTimeMillis() + 1000000)
                        .enabled(true)
                        .build());

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

        Assertions.assertThatThrownBy(() ->
                        apiKeyService.fetchVerifiedIdentity(apiKeyStr))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void fetchVerifiedIdentity_hashMatch_multipleKeys() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String prefix = ApiKeyGenerator.extractPrefixPart(apiKeyStr);
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final UserRef owner1 = UserRef.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();
        final UserRef owner2 = UserRef.builder()
                .uuid("myUuid2")
                .subjectId("mySubjectId2")
                .displayName("myDisplayName2")
                .build();
        final UserRef owner3 = UserRef.builder()
                .uuid("myUuid3")
                .subjectId("mySubjectId3")
                .displayName("myDisplayName3")
                .build();

        final List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .owner(owner1)
                        .apiKeyHash(hash)
                        .apiKeyPrefix(prefix)
                        .hashAlgorithm(HashAlgorithm.DEFAULT)
                        .expireTimeMs(System.currentTimeMillis() + 1000000)
                        .enabled(true)
                        .build(),
                HashedApiKey.builder()
                        .owner(owner2)
                        .apiKeyHash("and another hash")
                        .apiKeyPrefix(prefix)
                        .hashAlgorithm(HashAlgorithm.ARGON_2)
                        .expireTimeMs(System.currentTimeMillis() + 1000000)
                        .enabled(true)
                        .build(),
                HashedApiKey.builder()
                        .owner(owner3)
                        .apiKeyHash("and yet another hash")
                        .apiKeyPrefix(prefix)
                        .hashAlgorithm(HashAlgorithm.SHA3_256)
                        .expireTimeMs(System.currentTimeMillis() + 1000000)
                        .enabled(true)
                        .build());

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

        final User user1 = User.builder()
                .subjectId(owner1.getSubjectId())
                .uuid(owner1.getUuid())
                .displayName(owner1.getDisplayName())
                .build();
        Mockito.when(mockUserDao.getByUuid(Mockito.eq(owner1.getUuid())))
                .thenReturn(Optional.of(user1));

        final UserIdentity userIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr).orElseThrow();
        assertThat(userIdentity.subjectId())
                .isEqualTo(owner1.getSubjectId());
    }

    @Test
    void fetchVerifiedIdentity_hashMatch_expired() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String prefix = ApiKeyGenerator.extractPrefixPart(apiKeyStr);
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final UserRef owner1 = UserRef.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();

        final List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .owner(owner1)
                        .apiKeyPrefix(prefix)
                        .apiKeyHash(hash)
                        .hashAlgorithm(HashAlgorithm.DEFAULT)
                        .expireTimeMs(System.currentTimeMillis() - 1000)
                        .build());

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

        Assertions.assertThatThrownBy(() ->
                        apiKeyService.fetchVerifiedIdentity(apiKeyStr))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void fetchVerifiedIdentity_hashMatch_disabled() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String prefix = ApiKeyGenerator.extractPrefixPart(apiKeyStr);
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final UserRef owner1 = UserRef.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();

        final List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .owner(owner1)
                        .apiKeyPrefix(prefix)
                        .apiKeyHash(hash)
                        .hashAlgorithm(HashAlgorithm.DEFAULT)
                        .expireTimeMs(System.currentTimeMillis() + 1000000)
                        .enabled(false)
                        .build());

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

        Assertions.assertThatThrownBy(() ->
                        apiKeyService.fetchVerifiedIdentity(apiKeyStr))
                .isInstanceOf(AuthenticationException.class);
    }

    @TestFactory
    Stream<DynamicTest> testHashAlgorithms() {
        //noinspection VariableTypeCanBeExplicit
        final var builder = TestUtil.buildDynamicTestStream()
                .withInputType(HashAlgorithm.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final HashAlgorithm hashAlgorithm = testCase.getInput();
                    long millis = 0;
                    long nanos = 0;
                    final int cnt = 10;
                    for (int i = 0; i < cnt; i++) {
                        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();

                        final DurationTimer timer = DurationTimer.start();
                        final String hash = apiKeyService.computeApiKeyHash(
                                apiKeyStr,
                                hashAlgorithm);
                        final Duration duration = timer.get();
                        millis += duration.toMillis();
                        nanos += duration.toNanos();
                        LOGGER.info("Generated {} hash {} (len: {})", hashAlgorithm, hash, hash.length());
                        final boolean isValid = apiKeyService.verifyApiKeyHash(apiKeyStr, hash, hashAlgorithm);

                        assertThat(isValid)
                                .isTrue();
                    }
                    LOGGER.info("Generated {} {} hashes at {} ms ({} ns) per hash",
                            cnt,
                            hashAlgorithm,
                            ModelStringUtil.formatCsv(millis / cnt),
                            ModelStringUtil.formatCsv(nanos / cnt));
                    return true;
                })
                .withSimpleEqualityAssertion();

        for (final HashAlgorithm hashAlgorithm : HashAlgorithm.values()) {
            builder.addCase(hashAlgorithm, true);
        }

        return builder.build();
    }

    /// Make sure that we can verify a fixed set of api keys and their hashes. This is to ensure that
    /// any code change to the hashing functions doesn't break hashes that have already been computed.
    @TestFactory
    Stream<DynamicTest> testStaticHashes() {
        final List<HashOutput> hashOutputs = JsonUtil.getMapper().readValue(
                TEST_DATA,
                new TypeReference<>() {
                });

        final Set<HashAlgorithm> algorithmsInData = hashOutputs.stream()
                .map(HashOutput::hashAlgorithm)
                .collect(Collectors.toSet());
        Assertions.assertThat(algorithmsInData)
                .withFailMessage("The test data is out of date, run createTestData() to update it.")
                .containsExactlyInAnyOrder(HashAlgorithm.values());

        final AtomicInteger counter = new AtomicInteger(0);
        return hashOutputs.stream()
                .map(hashOutput ->
                        DynamicTest.dynamicTest(
                                counter.incrementAndGet() + " - " + hashOutput.hashAlgorithm.name(),
                                () -> {
                                    final boolean isValidHash = apiKeyService.verifyApiKeyHash(
                                            hashOutput.inputText,
                                            hashOutput.hash,
                                            hashOutput.hashAlgorithm);
                                    Assertions.assertThat(isValidHash)
                                            .isTrue();
                                }
                        )
                );
    }

    @Disabled // manual only, to see how many hash clashes we get for 10mil api keys (answer: 0 ish)
    @Test
    void testHashClash() {
        final int iterations = 10_000_000;
        final Set<String> hashes = new ConcurrentSkipListSet<>();

        final ThreadLocal<ApiKeyGenerator> apiKeyGeneratorThreadLocal = ThreadLocal.withInitial(ApiKeyGenerator::new);
        final LongAdder clashCount = new LongAdder();

        IntStream.range(0, iterations)
                .parallel()
                .mapToObj(i -> {
                    final ApiKeyGenerator apiKeyGenerator = apiKeyGeneratorThreadLocal.get();
                    return apiKeyGenerator.generateRandomApiKey();
                })
                .forEach(apiKey -> {
                    final String hash = apiKeyService.computeApiKeyHash(apiKey, HashAlgorithm.SHA3_256);
                    if (hashes.contains(hash)) {
                        clashCount.increment();
                    }
                    hashes.add(hash);
                });

        LOGGER.info("clashCount: {}, hashes: {}", clashCount, hashes.size());
    }

    @Disabled // manual only, to see how many prefix clashes we get for 10mil api keys (answer: 46 ish)
    @Test
    void testPrefixClash() {
        final int iterations = 10_000_000;
        final Set<String> prefixes = new ConcurrentSkipListSet<>();

        final ThreadLocal<ApiKeyGenerator> apiKeyGeneratorThreadLocal = ThreadLocal.withInitial(ApiKeyGenerator::new);
        final LongAdder clashCount = new LongAdder();

        IntStream.range(0, iterations)
                .parallel()
                .forEach(i -> {
                    final ApiKeyGenerator apiKeyGenerator = apiKeyGeneratorThreadLocal.get();
                    final String apiKey = apiKeyGenerator.generateRandomApiKey();
                    final String prefix = ApiKeyGenerator.extractPrefixPart(apiKey);
                    if (prefixes.contains(prefix)) {
                        clashCount.increment();
                    }
                    prefixes.add(prefix);
                });

        LOGGER.info("clashCount: {}, prefixes: {}", clashCount, prefixes.size());
    }

    @Disabled // Manual running only
    @Test
    void createTestData() {
        final int iterations = 5;

        final List<HashOutput> hashOutputs = new ArrayList<>(iterations * HashAlgorithm.values().length);
        for (final HashAlgorithm hashAlgorithm : HashAlgorithm.values()) {
            for (int i = 0; i < iterations; i++) {
                final String inputText = apiKeyGenerator.generateRandomApiKey();
                final String hash = apiKeyService.computeApiKeyHash(inputText, hashAlgorithm);
                hashOutputs.add(new HashOutput(inputText, hash, hashAlgorithm));
            }
        }
        final String json = JsonUtil.writeValueAsString(hashOutputs);
        System.out.println(json);
    }


    // --------------------------------------------------------------------------------


    private record HashOutput(
            String inputText,
            String hash,
            HashAlgorithm hashAlgorithm) {

    }
}
