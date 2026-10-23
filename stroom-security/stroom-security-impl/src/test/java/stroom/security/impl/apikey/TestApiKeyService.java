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

package stroom.security.impl.apikey;

import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.ApiKeyGenerator;
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

import java.security.SecureRandom;
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
              "inputText" : "sak_66e3d1414a_xFyAWU1cLMyRsYcg8QkqfHMMzboZ2q28MSoPs7B7wzMjSuMULHZkBsC8CnnUMdfQAon5q4RPXjVhawuMc96U42ff9NcuddrMQ9PeJztTECtfuzEst9undfX7HJtXmrDy",
              "hash" : "91793091e021c11ac60f63e2f085a54a30559a381e1c9aaaf59c48ce8a64ced9",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_a163f9508b_jqffgkoGZgsja9g4ioZrFB9NNqCbQUWaUQjHuZ31MjSMPtAyGRwJTpL9ZV6gPZPhVdGXqeGVy6E7CLBM5226s2fEXdfH29wictgVikqkjCWkw8EbWcDqCSvdVX9XnwPk",
              "hash" : "ba1a3ffa058a0ba208a17e8e4bccc8afc6e9cd3be583ab550d2e8dd88de5c910",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_c30330fa0a_4nYERAfKJWw6mgrjk85K4Gft45fRMDA7BBwN9fo9kCGgVvAmMU7JH9JX2KHHGS4NNfdttuLKBdMFZknznVuEtmuBEEi8KAc8hzr4mJr8SEj5hUShdjFmr6AvKzNrcFWp",
              "hash" : "635c8af20fefd7d731ab615519ec4a2e7eafedbf3cf6b8b671bbb347402a4f8f",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_1735e2b674_KNP6hFZd43QakuYhfqoQ7rWvpF7iGGWedMSaGUkXcFQUP2ELbjmM7vePmQUVpetrhfo1qqNCxRyxLKtyVPBLQVMBMfVk7z3cGr69Ebi5FbZh29ZzaRUCaD7ej1chTxvS",
              "hash" : "cad85c4d7e99d727d124ab233cb33449c757f2e6b740cd88012d62856f1c3bf9",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_ef8abb4659_vjwHBXKYGywPmT78wqLbr7tibvonxCmUnVVHW9rD6YdQNTX9xwFrozmipETA9EiwFHU2yFstcgc9qd8ACJPgunK1V1Exh6KWg5LbAanuvUD6Zukmjc3VnSBXhfdQD5ee",
              "hash" : "4f0fd33f8ebc4e171331375dc6c2119cdb9c1d47c29d727f36f6e9114e9f31f8",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_2b38bebe4f_6PFFFP7ZXcRBFvu8h1EfevTzBvRybRe28niDFASpxAg4i8JP6e7g8GsSgqEZZUxqrRHYvmYAuNSd48G3BvMPbVLHH6xQHqmJZAZENk4Ydv7zQ2ChLXRqA5XFrNMq9Cpw",
              "hash" : "cc5b8fb42303987c9641c5c833c2b17781c5b8c3c047b9a9ac6181191fb56db2",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_cf76142e34_z7PMw3Nd8ofpFJSPnQYu2kbftFqYHqrQqPP6M41RBZoj55JWfFiVQndMjLdxEexnRNfUey1m1ktzWPAnM3WSfC4wEjLTprQTU5XGTErEGXv98vj21ZuoyXvnpVxykseG",
              "hash" : "ccbd8c030dea745ce2ba876521a8519e65f8ea2a89015218bbb06e8454037a5c",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_d3b5ae203f_URpBwC9CaZTCdQPX4ieaANzsxT7C1nZJTMciJkVwEdezqYmXtX4oTgKWSzLcakcmzD1Lmeo4JvsEH28LgMsDXAmQ7Ve9v2GmKUTJBeatMteV9czL1yUnRzopeJesQrXv",
              "hash" : "171800e34bc95a1ba558b9645d2728275667e755f9fc09a5398ea663d9a6f620",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_286d5745d5_6hBTqiub9XVTof7s7yUmAVW47qadYNSxBwjdgVprFFGc1eTAavs5EQJsfbpmPF3FjPqsrodaZ9AicCJtwrbD3AvjLKHes9uWDfipVS6jxnJuXAWBzYNfsF9Fx4wCqJJz",
              "hash" : "84a29b7f177d7b6790b07a8767e2c45337c90682d57bb9079132e6d8351fd394",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_be9db44134_VeeBYWjLPedqpQMyi5XBNCbvThpp56MRtJUBMDNbw6FEqRFQxAvHy44NGfDf849P8SUntgs7zi92xKophjFVZawiHRiDxyXG88y1V49LAaaD2dGFwRTwQR87UTviGRUx",
              "hash" : "4923fd4cc7e0068c10e1528824532ad93d923660cbba9ec6bf3cb6b03ae80f2b",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_d7bc15d392_V97io91iDG7xsbadFEM45n9gRgV3K7GcRaQp3ym5yCL542im1Xf4F1LKFMfQ4dwPWKvoaDJakGej2ch7hjgjBECfUQdWhMF7PfuezX447ABUpwTDKPEMMDHw3DRVhm5v",
              "hash" : "$2a$10$QKwhCZkaYC79pEl5Inkw3e9hIS0xsYszlZWM/IA0A2cwGsgV5R/om",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_46227f48e5_CruMP7snFswerQhjAqdjDeMS6oaVqc2sfjRbDBmL9R7LRbMSwY1QUkGqZ17H88tRqratXU639S7AsnTo2nz4UPzWarrjtW4waTgZu7MheGAvC6o3DHrmvgx7N3mQsPLW",
              "hash" : "$2a$10$dEo4tU.v6UFWA23cwzdD5e5B5XuJ97bxl7rzIHbA0sDnuTh9JdWn2",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_20f08a01ac_DuGaSN1Rf7rrLzRxg8EWRdwtNsWFH8b7bNdQQ7kQ7Sj3d1JupVJ9PyYyXnbo71jH1FgLECPWcUNJ3ahjZyReU515r4RSvZizouoKDMFCf9vYHJN4SWiU3ufsA3Yg3Us7",
              "hash" : "$2a$10$KshqnX9m8d4u5CO3TmhxV.ZOZAAWw0trQZUs5q.MaZqcrZCutGCt.",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_2eb002480b_uMcZgEPujGAdng7LwoeiUu5q9vWWWpVxc1V27heAM7kYmbgSnx4QD6KbmG1PL67M4kSwCpLFwBuk5Ac8sgcv1UHCAMxczsPcxkNVE6JAsCu8wyUWCphYYaryRSH2zvmr",
              "hash" : "$2a$10$UJke6tJ2D8Mkw7pYjANBUebh3XW1.LizOXr4IXnkdogbSMf/0xiNO",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_9ca1a02696_e9Pndi3pdnen38MDAQqZSEHKUMUkpdtVauHYxLm7zpU7DANmuGLabcFB8mwivrtNvhxohUwP9ELbJrJnugdZdrG1Jn8H6TWzizpqp5kds7kG2oWVXdaMrjL3qKKBrFuV",
              "hash" : "$2a$10$MNe5ghTPD1hkLvUIHXXXsOuqX5Ojdhaa7WUCjtElt2G0hrfViF8/q",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "sak_bc24a625d0_pu8um6p8h3EkajBGfNrwB9EYY5hZQsXY5NrEAXL4tUckBWDBfipGVBAFxdMEFd2KHyCAFBXfpWsq6m1t8n1aBodKHW94QRVemWHZ5FUwdezaTP73cNhD5vkN53WzBAgP",
              "hash" : "9gTTWaUeoze676VswsX4YF2KgwstXEapt9eEoo4Lka2FnUv48KbK7q8J4si7v4FPkR",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_3668d152d4_MmEU3RpdXihqdE2dNyvHAYmsfkBbCD9rQysnGGUDo5UDhYAEa2R8wZWiRF4uLwndfZFTFJ7u7sCUfyzfGcP4BN1RsyMsncadodtnitfMt2g7E9rsa82iDzWHBSQfmN3A",
              "hash" : "3HLXqYws9nTFLaZzsbDNkidCfQozqcQ34sKmryj71rpaCxgkV6tf3qZGHzDHLmfrLH",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_41ee08afee_oWd3rZWzaMqDe6mXdG7cJc8cmY74GYtZfWTLKWXsYQdg4Q6sqcPkzusb8njCmsN279rQfqwbGXoGF5zov7Zp3A8BJ2GZrRYDzGRka32dT7uxAoxJ3jcjteJGqmq96ubn",
              "hash" : "4m162TWnsGE8y4m6RxD7uuqdEyLvUH5dMk6cs5YdxEZiwFrovjYCF8ZD6aF6du1w5Q",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_333d8e99ea_q72zZy6Aa3qvzGKp1MxGFEX4JRvPHuwaeAzLsqZhVR6ct8BPd9EdNu19LZNaKrgjb3ptWdvqVn6Hmhq5zqaXPjasMa5fa3wCejPamQGjahi628UhmNhtMcBy7akRMiGK",
              "hash" : "3sL7BXb5wg6b5GN4HeyAwcJYe6Gbv36kzs2EJ5NZmsNoxeUmLwFvqvjJAyyAL12WvS",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_000fa7fb1f_Tw5ydLtWgFqZ7tDbpfHiEmwLZM6hnTRRYqg4j6YYaXepNyNW8ZBFxENRpjZZ2tQLXhmKbTYvgWYMxq1NjK3gtJZ1NvxS5P5eft1XkMsoaZ5DRGbgSPd19rpEmSjDPhS1",
              "hash" : "A2FA1hZyPwJyEsLtQQqvn9kzw73WTHmczkMTNChx77JsfPSqafDUkNiLFNvVEHmP9L",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "sak_d8faa91a5c_2DfK3WCtXV69uefNWVnsKt25JHoLRFB7eX3uvBd1Fu9tcagUo6oXk3n8ZkcA7XJqpsr9dtSsGiEBK65dAHhhNokhfsWX6ds3rwjBh39wD1vvz3b1DSdYTHtbfXX29hUW",
              "hash" : "2730bf1c922619a2ed66671dbfa5c62465506b01bd0aee53714b5583d96311fa5dc261dc951e5a9f86c8bf670d3dd37dee34d72078c0645ea504537b5b4c8ea2",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_601a56cf18_ptaYuWBLBF8eqfgEJMfcvfV8DmY4R2c7TG7g3kMjNFzmDDdn6Ue1n4sRNN4RsTZKVaXyktRMfcrmKED9xvEiH27adXd3nfvPrUJGuYMwAbiMRaLax6zGuncZigDEGYVb",
              "hash" : "7c133b1409519172f96750d954192681fcccebe257ba1a516b5a28e43b0488826358261927b8b997b8085c25f7db710b6112c83ec78811a84baebf9c7ce49cd5",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_df2559cd84_idckXCzQ2kJq8hCxjKzgARuDG79wCujjVtaidhnXakMJt2PwQNLCxgB1nKKJJCLGDE5A1hFSBJ1bCgTPRFhysTiyW7jd8vwLWjUP2rds67qkUNZRNHNfNP7vxmhUnNVM",
              "hash" : "17d5b49577bee4ceb88c95341c32e014eb9a3707593bb6934d6e18eb2c5026aebe6965f1deaac63df9464be779d49e38c2de483f1b390b31606d185c75e64635",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_230782c3bf_U9XZMr1WhSWsrJWE1S26xQMYGZkUGqpNzbS1fE5ctV5THh7SDnfFudDBru2i2SCzzB2J2H1h6jq9hAhr5ZEUeWdAz1wcFRDzKuAzUHXYfLW2XDbeV2sc8aWXFjWDbRhe",
              "hash" : "c7d16ed98c868d9b4ddab9ce0154f7e31ee315a0a82189be8b572d8603467a754c0997ba49656c94bf84af2eebf8c7e9cd2963139e96f3d8bb2b489f65b1ea16",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_4c5a62e0e7_xMf28DWEZztcpps9t4iMFMXqWp3Z3vjaSyf8T3LoGzaeSjMcVtDnd8ZM2w2tgb4AFgvSNEy7SCYAMM86sDH752Zdmd3MmCfy7vKxP1Y83pZXDSfM3PfesYbczcwdEr2g",
              "hash" : "99bc595af5d596fffa030120aedeb7c7d10e88f44f156a3b6f6d20a88e212ec1bcacfd32bb1fc64b7d8f0db11e79fbeb192acc0ab35987006c4d37e6af0e2911",
              "hashAlgorithm" : "SHA2_512"
            } ]""";

    private final SecurityContext securityContext = new MockSecurityContext();
    private final ApiKeyGenerator apiKeyGenerator = new ApiKeyGenerator();
    private final SecureRandom secureRandom = new SecureRandom();

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
                mockEntityEventBus);
    }

    @Test
    void verifyApiKey_validKey_returnsUserDescription() {
        final String apiKey = apiKeyGenerator.generateRandomApiKey();
        final User owner = createUser("mySubjectId");

        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(List.of(HashedApiKey.builder()
                        .owner(owner.asRef())
                        .apiKeyHash(apiKeyService.computeApiKeyHash(apiKey))
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
                mockEntityEventBus);

        final String apiKey = apiKeyGenerator.generateRandomApiKey();
        final User owner = createUser("mySubjectId");
        Mockito.when(mockApiKeyDao.fetchApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(List.of(HashedApiKey.builder()
                        .owner(owner.asRef())
                        .apiKeyHash(apiKeyService.computeApiKeyHash(apiKey))
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
