/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.config.global.shared.ConfigProperty;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Masks secrets in system property values before the agent sees them. It errs on the side of masking:
///
/// - every value of a property marked as a password is masked, as the Properties screen masks it;
/// - so is every value of a property whose name suggests a secret (e.g. `clientSecret`, `secretEncryptionKey`,
///   `apiToken`), as not all of them are marked as passwords, unless its type can't hold one (a number, a
///   boolean, a duration or a size);
/// - in any other value, credentials written into it are masked: the password in a URL's `user:password@`, the
///   value of a key named like a secret in `key=value`, `key: value`, `"key": "value"` or `<key>value</key>`
///   (e.g. `ssl.key.password=...` in a Kafka properties value), the values of such keys in a map property,
///   and `Bearer`/`Basic` credentials.
///
/// The patterns are fixed and linear, so they are safe to run on any value.
final class ConfigValueMasker {

    /// What a masked value is shown as, as [ConfigProperty#getEffectiveValueMasked()] shows a password.
    static final String MASK = "********************";

    private static final String SECRET_WORDS = "(?:password|passwd|pwd|passphrase|secret|token|apikey|api[_-]key"
                                               + "|private[_-]?key|credentials?)";
    /// A property name that suggests a secret. A name only ending in `token` is matched, so that e.g.
    /// `tokenEndpoint` (a URL) is not.
    private static final Pattern SECRET_NAME = Pattern.compile(
            "(?i).*(?:password|passwd|passphrase|secret|apikey|api_key|privatekey|private_key|credential).*"
            + "|.*token");
    /// The types of property whose values can't be a secret, by [ConfigProperty#getDataTypeName()].
    private static final Set<String> SAFE_TYPES = Set.of(
            "Boolean", "Integer", "Long", "Short", "Byte", "Double", "Float",
            "StroomDuration", "SimpleDuration", "ByteSize");
    /// A key in a map value that suggests a secret.
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*" + SECRET_WORDS + ".*");
    private static final Pattern URL_USER_INFO = Pattern.compile(
            "(?i)\\b([a-z][a-z0-9+.\\-]*://)([^/\\s:@]*):([^/\\s@]+)@");
    private static final Pattern QUOTED_PAIR = Pattern.compile(
            "(?i)(\"[\\w.\\-]*" + SECRET_WORDS + "[\\w.\\-]*\"\\s*:\\s*\")((?:[^\"\\\\]|\\\\.)*)(\")");
    private static final Pattern XML_PAIR = Pattern.compile(
            "(?i)(<([\\w.\\-]*" + SECRET_WORDS + "[\\w.\\-]*)>)([^<]*)(</\\2>)");
    private static final Pattern PLAIN_PAIR = Pattern.compile(
            "(?i)([\\w.\\-]*" + SECRET_WORDS + "[\\w.\\-]*)(\\s*[=:]\\s*)([^\\s&;\"'<>]+)");
    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=\\-]{8,}");
    /// The characters Stroom may use to separate the items of a list or map value (see ConfigMapper).
    private static final String DELIMITERS = "|:;,!/\\#@~-_=+?";

    private ConfigValueMasker() {
        // Static only.
    }

    /// @param property A property.
    /// @return True if all its values are masked, as it is a password or its name suggests a secret.
    static boolean isSecret(final ConfigProperty property) {
        if (property.isPassword()) {
            return true;
        }
        final String name = NullSafe.get(property.getName(), path -> path.getPropertyName());
        return name != null
               && SECRET_NAME.matcher(name).matches()
               && !SAFE_TYPES.contains(NullSafe.string(property.getDataTypeName()));
    }

    /// @param property The property the value is of.
    /// @param value    One of its values (default, database, YAML or effective), or null.
    /// @return The value, masked as it needs to be, or null if it was null.
    static String mask(final ConfigProperty property, final String value) {
        if (value == null) {
            return null;
        }
        if (isSecret(property)) {
            return MASK;
        }
        if (NullSafe.string(property.getDataTypeName()).startsWith("Map of")) {
            return maskMapEntries(value);
        }
        return maskEmbedded(value);
    }

    /// @param value Any text, e.g. a property's value.
    /// @return The text, with the credentials written into it masked.
    static String maskEmbedded(final String value) {
        if (NullSafe.isEmptyString(value)) {
            return value;
        }
        String masked = URL_USER_INFO.matcher(value).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1) + match.group(2) + ":" + MASK + "@"));
        masked = QUOTED_PAIR.matcher(masked).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1) + MASK + match.group(3)));
        masked = XML_PAIR.matcher(masked).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1) + MASK + match.group(4)));
        masked = PLAIN_PAIR.matcher(masked).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1) + match.group(2) + MASK));
        return AUTH_SCHEME.matcher(masked).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1) + " " + MASK));
    }

    /// Masks the values of the keys named like secrets in a map value, as Stroom writes one: its first character
    /// separates the entries and its second each key from its value, e.g. `|=user=bob|password=x`. The other
    /// entries are masked as any other value is, each on its own, so a masked value doesn't run into the next
    /// entry. A value not written as a map is masked as any other value is.
    private static String maskMapEntries(final String value) {
        if (value.length() < 2
            || DELIMITERS.indexOf(value.charAt(0)) < 0
            || DELIMITERS.indexOf(value.charAt(1)) < 0) {
            return maskEmbedded(value);
        }
        final String entryDelimiter = String.valueOf(value.charAt(0));
        final String keyValueDelimiter = String.valueOf(value.charAt(1));
        final List<String> entries = new ArrayList<>();
        for (final String entry : value.substring(2).split(Pattern.quote(entryDelimiter), -1)) {
            final int split = entry.indexOf(keyValueDelimiter);
            if (split > 0 && isSecretKey(entry.substring(0, split))) {
                entries.add(entry.substring(0, split) + keyValueDelimiter + MASK);
            } else {
                // The rest of the entry may still hold credentials, e.g. a URL.
                entries.add(maskEmbedded(entry));
            }
        }
        return value.substring(0, 2) + String.join(entryDelimiter, entries);
    }

    private static boolean isSecretKey(final String key) {
        return SECRET_KEY.matcher(key).matches();
    }
}
