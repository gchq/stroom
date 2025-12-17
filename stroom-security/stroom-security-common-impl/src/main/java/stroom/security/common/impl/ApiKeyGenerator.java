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

package stroom.security.common.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.Base58;
import stroom.util.string.StringUtil;

import jakarta.inject.Singleton;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Singleton // Due to shared SecureRandom
public class ApiKeyGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyGenerator.class);

    // Stands for stroom-api-key
    static final String API_KEY_TYPE = "sak";
    static final String API_KEY_SEPARATOR = "_";
    public static final String API_KEY_STATIC_PREFIX = API_KEY_TYPE + API_KEY_SEPARATOR;
    private static final Pattern API_KEY_SEPARATOR_PATTERN = Pattern.compile(API_KEY_SEPARATOR, Pattern.LITERAL);
    public static final int API_KEY_RANDOM_CODE_LENGTH = 128;
    // A sha256 truncated to 10 typically gives less than a handful of clashes for 1mil random codes
    public static final int TRUNCATED_HASH_LENGTH = 10;
    public static final int PREFIX_LENGTH = API_KEY_TYPE.length()
                                            + (API_KEY_SEPARATOR.length() * 2)
                                            + TRUNCATED_HASH_LENGTH;
    public static final int API_KEY_TOTAL_LENGTH = PREFIX_LENGTH + API_KEY_RANDOM_CODE_LENGTH;

    private static final String BASE_58_ALPHABET = new String(Base58.ALPHABET);
    private static final String BASE_58_CHAR_CLASS = "[" + BASE_58_ALPHABET + "]";
    private static final String HEX_CHAR_CLASS = "[0-9a-f]";

    // A regex pattern that will match a full api key
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "^"
            + Pattern.quote(API_KEY_TYPE + API_KEY_SEPARATOR)
            + HEX_CHAR_CLASS + "{" + TRUNCATED_HASH_LENGTH + "}"
            + Pattern.quote(API_KEY_SEPARATOR)
            + BASE_58_CHAR_CLASS + "{" + API_KEY_RANDOM_CODE_LENGTH + "}"
            + "$");
    private static final Predicate<String> API_KEY_MATCH_PREDICATE = API_KEY_PATTERN.asMatchPredicate();

    private final SecureRandom secureRandom;

    public ApiKeyGenerator() {
        this.secureRandom = new SecureRandom();
        LOGGER.debug("API_KEY_PATTERN: '{}'", API_KEY_PATTERN);
    }

    /**
     * Asserts that the passed string matches the format of a stroom API key and that the hash part of
     * the key matches the hash computed from the random code part.
     * Note, this method is NOT to be used for authenticating
     * an API key. It simply verifies
     * that a string is very likely to be a stroom API key (whether valid/invalid, present/absent, enabled/disabled).
     * It can be used to see if a string passed in the {@code Authorization: Bearer ...} header is a stroom
     * API key before looking in the database to check its validity.
     *
     * @return True if the hash part of the API key matches a computed hash of the random
     * code part of the API key and the string matches the pattern for an API key.
     */
    public boolean isApiKey(final String apiKey) {
        if (NullSafe.isBlankString(apiKey)) {
            return false;
        } else {
            final String trimmedApiKey = apiKey.trim();
            if (!trimmedApiKey.startsWith(API_KEY_STATIC_PREFIX)) {
                LOGGER.debug(() -> LogUtil.message("Doesn't start with api key static prefix '{}'",
                        API_KEY_STATIC_PREFIX));
                return false;
            }
            if (!API_KEY_MATCH_PREDICATE.test(trimmedApiKey)) {
                LOGGER.debug("Doesn't match pattern '{}'", API_KEY_PATTERN);
                return false;
            }
            final String[] parts = API_KEY_SEPARATOR_PATTERN.split(apiKey.trim());
            if (parts.length != 3) {
                LOGGER.debug("Incorrect number of parts: '{}', parts:", (Object) parts);
                return false;
            }
            final String hashPart = parts[1];
            final String codePart = parts[2];
            final String computedHash = computeTruncatedHash(codePart);
            if (!Objects.equals(hashPart, computedHash)) {
                LOGGER.debug("Hashes don't match, hashPart: '{}', computedHash: '{}'", hashPart, computedHash);
                return false;
            }
            return true;
        }
    }

    /**
     * Generate a random API key of the form
     * <pre>{@code sak_<truncated hash>_<random code>}</pre>
     * where:
     * <p>
     * {@code <hash>} is the SHA2-256 hash of {@code <random code>} and truncated to the first
     * ten characters. This acts as a checksum for the random code part so you can be confident that something
     * that looks like a stroom api key is actually one. As the prefix is stored in Stroom, it serves as a way
     * for the user to match a key they have with the API Key record in stroom (that doesn't hold the full key).
     * </p>
     * <p>
     * {@code <random code>} is a string of 128 crypto random characters using the Base58 character set.
     * </p>
     *
     * <p>The following <a href="https://gchq.github.io/CyberChef/">cyberchef</a> recipe takes a full API key
     * as input and highlights the hash part if it is valid, thus indicating that the API key is a valid one.
     * </p>
     * <pre>
     *
     * Register('sak_(.*)_.*$',true,false,false)
     * Regular_expression('User defined','sak_.*_(.*)$',true,true,false,false,false,false,'List capture groups')
     * SHA2('256',64,160)
     * Regular_expression('User defined','^.{10}',true,true,false,false,false,false,'List matches')
     * Regular_expression('User defined','^$R0$',true,true,false,false,false,false,'Highlight matches')
     *
     * </pre>
     */
    public String generateRandomApiKey() {
        // This is the meat of the API key. A random string of chars using the
        // base58 character set. THis is so we have a nice simple set of readable chars
        // without visually similar ones like '0OIl'
        final String randomCode = StringUtil.createRandomCode(
                secureRandom,
                API_KEY_RANDOM_CODE_LENGTH,
                StringUtil.ALLOWED_CHARS_BASE_58_STYLE);

        // Generate a short hash of the randomCode. This is not THE hash. It just acts as a
        // checksum for the random code part of the key, so you can reasonably confidently
        // tell if a string is a stroom api key or not.
        final String randomCodeHash = computeTruncatedHash(randomCode);
        return String.join(API_KEY_SEPARATOR, API_KEY_TYPE, randomCodeHash, randomCode);
    }

    private static String computeTruncatedHash(final String randomStr) {
        // Now get a sha2-256 hash of our random string and truncated to 10 chars.
        // This part acts as a checksum of the random code part and provides a means to verify that
        // something that looks like a stroom API key is actually one, e.g. for spotting stroom API keys
        // left in the clear. We may never use this checksum, but you never know.
        // See https://github.blog/2021-04-05-behind-githubs-new-authentication-token-formats/
        // for the idea behind this.
        final String sha256 = DigestUtils.sha256Hex(randomStr);
        return sha256.substring(0, TRUNCATED_HASH_LENGTH);
    }

    /**
     * For a key like
     * <pre>{@code
     * sak_50910c4ef3_Lz3mnbGYS14fn14LWZFvCn2wPD2bB.....yFimeDifW2FZmj8TixAAgGMM1BeJLpT5cN5ztTuNK3SZ8JZogL
     * }</pre>
     * return
     * <pre>{@code
     * sak_50910c4ef3_
     * }</pre>
     * This is to allow a user to identify their key. This prefix part may not be unique as it is only 7 chars of the
     * hash, however it probably is, so is good enough.
     */
    public static String extractPrefixPart(final String apiKey) {
        Objects.requireNonNull(apiKey);
        final String[] parts = API_KEY_SEPARATOR_PATTERN.split(apiKey.trim());
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid key format, expecting three parts.");
        }
        return parts[0] + API_KEY_SEPARATOR + parts[1] + API_KEY_SEPARATOR;
    }

    /**
     * Return true if the prefix portion of the two strings match exactly. Either argument
     * can be of any length greater than or equal to {@link ApiKeyGenerator#PREFIX_LENGTH}.
     */
    public static boolean prefixesMatch(final String apiKey1, final String apiKey2) {
        if (NullSafe.allNull(apiKey1, apiKey2)) {
            return false;
        } else if (apiKey1 == null) {
            return false;
        } else if (apiKey2 == null) {
            return false;
        } else {
            return apiKey1.regionMatches(0, apiKey2, 0, PREFIX_LENGTH);
        }
    }


    // --------------------------------------------------------------------------------


//    record ApiKeyParts(String type, String hash, String randomCode) {
//
//        static ApiKeyParts fromApiKey(final String apiKey) {
//            Objects.requireNonNull(apiKey);
//            final String[] parts = API_KEY_SEPARATOR_PATTERN.split(apiKey.trim());
//            if (parts.length != 3) {
//                throw new IllegalArgumentException("Invalid key format, expecting three parts.");
//            }
//            return new ApiKeyParts(parts[0], parts[1], parts[2]);
//        }
//
//        String asApiKey() {
//            return String.join(API_KEY_SEPARATOR, type, hash, randomCode);
//        }
//
//        /**
//         * For a key like
//         * <pre>{@code
//         * sak_50910c4ef3_Lz3mnbGYS14fn14LWZFvCn2wPD2bB.....yFimeDifW2FZmj8TixAAgGMM1BeJLpT5cN5ztTuNK3SZ8JZogL
//         * }</pre>
//         * return
//         * <pre>{@code
//         * sak_50910c4ef3_
//         * }</pre>
//         * This is to allow a user to identify their key. This prefix part may not be unique as it is only
//         * 10 chars of the hash, however it probably is, so is good enough.
//         */
//        String asPrefix() {
//            return String.join(API_KEY_SEPARATOR, type, hash) + API_KEY_SEPARATOR;
//        }
//    }
}
