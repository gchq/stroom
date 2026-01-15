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

package stroom.receive.rules.impl;

import stroom.meta.api.StandardHeaderArguments;
import stroom.query.api.datasource.FieldType;
import stroom.security.shared.HashAlgorithm;
import stroom.util.collections.CollectionUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
public class StroomReceiptPolicyConfig extends AbstractConfig implements IsStroomConfig {

    public static final Set<String> DEFAULT_OBFUSCATED_FIELDS =
            CollectionUtil.asUnmodifiabledConsistentOrderSet(List.of(
                    StandardHeaderArguments.ACCOUNT_ID,
                    StandardHeaderArguments.ACCOUNT_NAME,
                    StandardHeaderArguments.COMPONENT,
                    StandardHeaderArguments.FEED,
                    StandardHeaderArguments.RECEIVED_PATH,
                    StandardHeaderArguments.REMOTE_DN,
                    StandardHeaderArguments.REMOTE_HOST,
                    StandardHeaderArguments.SYSTEM,
                    StandardHeaderArguments.UPLOAD_USERNAME,
                    StandardHeaderArguments.UPLOAD_USER_ID,
                    StandardHeaderArguments.X_FORWARDED_FOR
            ));

    // Use sha2-512 to make hash clashes very unlikely.
    public static final HashAlgorithm DEFAULT_HASH_ALGORITHM = HashAlgorithm.SHA2_512;

    public static final Map<String, String> DEFAULT_INITIAL_RECEIPT_RULE_FIELDS =
            CollectionUtil.linkedHashMapBuilder(String.class, String.class)
                    .add(StandardHeaderArguments.ACCOUNT_ID, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.COMPONENT, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.COMPRESSION, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.CONTENT_LENGTH, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.CONTEXT_ENCODING, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.CONTEXT_FORMAT, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.EFFECTIVE_TIME, FieldType.DATE.getTypeName())
                    .add(StandardHeaderArguments.ENCODING, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.ENVIRONMENT, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.FEED, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.FORMAT, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.RECEIPT_ID, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.RECEIPT_ID_PATH, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.RECEIVED_PATH, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.RECEIVED_TIME, FieldType.DATE.getTypeName())
                    .add(StandardHeaderArguments.RECEIVED_TIME_HISTORY, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.REMOTE_CERT_EXPIRY, FieldType.DATE.getTypeName())
                    .add(StandardHeaderArguments.REMOTE_DN, FieldType.TEXT.getTypeName())
                    // Both of these could be one of fqdn/ipv4/ipv6, so TEXT it is
                    .add(StandardHeaderArguments.REMOTE_HOST, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.REMOTE_ADDRESS, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.SCHEMA, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.SCHEMA_VERSION, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.SYSTEM, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.TYPE, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.UPLOAD_USERNAME, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.UPLOAD_USER_ID, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.USER_AGENT, FieldType.TEXT.getTypeName())
                    .add(StandardHeaderArguments.X_FORWARDED_FOR, FieldType.TEXT.getTypeName())
                    .build();

    @JsonProperty
    private final Set<String> obfuscatedFields;
    @JsonProperty
    private final HashAlgorithm obfuscationHashAlgorithm;
    // fieldName => fieldTypeName (any case)
    @JsonProperty
    private final Map<String, String> receiptRulesInitialFields;

    public StroomReceiptPolicyConfig() {
        obfuscatedFields = DEFAULT_OBFUSCATED_FIELDS;
        obfuscationHashAlgorithm = DEFAULT_HASH_ALGORITHM;
        receiptRulesInitialFields = DEFAULT_INITIAL_RECEIPT_RULE_FIELDS;
    }

    @JsonCreator
    public StroomReceiptPolicyConfig(
            @JsonProperty("obfuscatedFields") final Set<String> obfuscatedFields,
            @JsonProperty("obfuscationHashAlgorithm") final HashAlgorithm obfuscationHashAlgorithm,
            @JsonProperty("receiptRulesInitialFields") final Map<String, String> receiptRulesInitialFields) {

        this.obfuscatedFields = NullSafe.getOrElse(
                obfuscatedFields,
                StroomReceiptPolicyConfig::cleanSet,
                DEFAULT_OBFUSCATED_FIELDS);
        this.obfuscationHashAlgorithm = Objects.requireNonNullElse(
                obfuscationHashAlgorithm, DEFAULT_HASH_ALGORITHM);
        this.receiptRulesInitialFields = Objects.requireNonNullElse(
                receiptRulesInitialFields, DEFAULT_INITIAL_RECEIPT_RULE_FIELDS);
    }

    private StroomReceiptPolicyConfig(final Builder builder) {
        this(
                builder.obfuscatedFields,
                builder.obfuscationHashAlgorithm,
                builder.receiptRulesInitialFields);
    }

    public Builder copy() {
        return copy(this);
    }

    public static Builder copy(final StroomReceiptPolicyConfig copy) {
        final Builder builder = new Builder();
        builder.obfuscatedFields = copy.getObfuscatedFields();
        builder.obfuscationHashAlgorithm = copy.getObfuscationHashAlgorithm();
        builder.receiptRulesInitialFields = copy.getReceiptRulesInitialFields();
        return builder;
    }

    @JsonPropertyDescription("The set of field names used in receipt data policy that need to be obfuscated " +
                             "(using a hash function) when transferred to stroom-proxy.")
    public Set<String> getObfuscatedFields() {
        return obfuscatedFields;
    }

    @JsonPropertyDescription("The hash algorithm to use for obfuscating fields defined in obfuscatedFields. " +
                             "Possible values are SHA3_256, SHA2_256, BCRYPT, ARGON_2.")
    public HashAlgorithm getObfuscationHashAlgorithm() {
        return obfuscationHashAlgorithm;
    }

    @JsonPropertyDescription("A map of field name to field type to use as the initial set of fields in the " +
                             "Data Receipt Rules screen. Case-insensitive. Valid field types are " +
                             "(Id|Boolean|Integer|Long|Float|Double|Date|Text|IpV4Address).")
    public Map<String, String> getReceiptRulesInitialFields() {
        return receiptRulesInitialFields;
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "receiptRulesInitialFields must contain non-null & non-blank keys/values. " +
                                "Field types must also be valid.")
    public boolean isReceiptRulesInitialFieldsValid() {
        if (receiptRulesInitialFields != null) {
            return receiptRulesInitialFields.entrySet()
                    .stream()
                    .allMatch(entry -> {
                        final String typeName = entry.getValue();
                        return NullSafe.isNonBlankString(entry.getKey())
                               && NullSafe.isNonBlankString(typeName)
                               && FieldType.fromTypeName(typeName) != null;
                    });
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        return "StroomReceiptPolicyConfig{" +
               "obfuscatedFields=" + obfuscatedFields +
               ", obfuscationHashAlgorithm=" + obfuscationHashAlgorithm +
               ", receiptRulesInitialFields=" + receiptRulesInitialFields +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StroomReceiptPolicyConfig that = (StroomReceiptPolicyConfig) o;
        return Objects.equals(obfuscatedFields, that.obfuscatedFields)
               && obfuscationHashAlgorithm == that.obfuscationHashAlgorithm
               && Objects.equals(receiptRulesInitialFields, that.receiptRulesInitialFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(obfuscatedFields, obfuscationHashAlgorithm, receiptRulesInitialFields);
    }

    private static Set<String> cleanSet(final Set<String> set) {
        return CollectionUtil.cleanItems(set, String::trim);
    }

    public static Builder builder() {
        return copy(new StroomReceiptPolicyConfig());
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private Set<String> obfuscatedFields;
        private HashAlgorithm obfuscationHashAlgorithm;
        private Map<String, String> receiptRulesInitialFields;

        private Builder() {
        }

        public Builder withObfuscatedFields(final Set<String> obfuscatedFields) {
            this.obfuscatedFields = obfuscatedFields;
            return this;
        }

        public Builder withObfuscationHashAlgorithm(final HashAlgorithm obfuscationHashAlgorithm) {
            this.obfuscationHashAlgorithm = obfuscationHashAlgorithm;
            return this;
        }

        public Builder withReceiptRulesInitialFields(final Map<String, String> receiptRulesInitialFields) {
            this.receiptRulesInitialFields = receiptRulesInitialFields;
            return this;
        }

        public StroomReceiptPolicyConfig build() {
            return new StroomReceiptPolicyConfig(this);
        }
    }
}
