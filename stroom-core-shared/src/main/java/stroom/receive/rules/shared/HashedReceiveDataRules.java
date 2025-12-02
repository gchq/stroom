package stroom.receive.rules.shared;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.security.shared.HashAlgorithm;
import stroom.util.shared.NullSafe;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Essentially this wraps a {@link ReceiveDataRules} instance except that the {@link ReceiveDataRules}
 * instance may have had the values and/or dictionary content obfuscated for certain fields.
 * It also contains the hash algorithm and salts used to allow other values to be similarly
 * obfuscated for evaluating against the expressions.
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class HashedReceiveDataRules {

    public static final String HASHED_FIELD_NAME_SUFFIX = "___!hashed!";

    /**
     * The time this snapshot of the rules was taken. Mostly here for debugging.
     */
    @JsonProperty
    private final long snapshotTimeEpochMs;

    @JsonProperty
    private final ReceiveDataRules receiveDataRules;
    /**
     * All {@link DictionaryDoc}s that are used by receiveDataRules.
     * Each {@link DictionaryDoc} must be flattened, i.e. have all it's imports resolved
     * to create a single combined {@link DictionaryDoc}.
     * If the dictionary is used by a field that is hashed then each line in the combined
     * dictionary will be hashed.
     */
    @JsonProperty
    private final Map<String, DictionaryDoc> uuidToFlattenedDictMap;

    /**
     * One random salt per fieldName. This does mean we may re-use a salt, i.e.
     * in the case of a dictionary of values, but there is not a lot we can do about that.
     * Field names all in lower-case.
     */
    @JsonProperty
    private final Map<String, String> fieldNameToSaltMap;

    /**
     * The hash algorithm used to hash the hashed field values.
     */
    @JsonProperty
    private final HashAlgorithm hashAlgorithm;

    /**
     * @param fieldNameToSaltMap A {@link Map} of field name (lower-case) to the salt used to hash values for
     *                           that field.
     */
    @JsonCreator
    public HashedReceiveDataRules(
            @JsonProperty("snapshotTimeEpochMs") final long snapshotTimeEpochMs,
            @JsonProperty("receiveDataRules") final ReceiveDataRules receiveDataRules,
            @JsonProperty("uuidToFlattenedDictMap") final Map<String, DictionaryDoc> uuidToFlattenedDictMap,
            @JsonProperty("fieldNameToSaltMap") final Map<String, String> fieldNameToSaltMap,
            @JsonProperty("hashAlgorithm") final HashAlgorithm hashAlgorithm) {

        this.snapshotTimeEpochMs = snapshotTimeEpochMs;
        this.receiveDataRules = Objects.requireNonNull(receiveDataRules);
        this.uuidToFlattenedDictMap = NullSafe.map(uuidToFlattenedDictMap);
        this.fieldNameToSaltMap = NullSafe.map(fieldNameToSaltMap);
        this.hashAlgorithm = hashAlgorithm;
        validateValues(
                this.receiveDataRules,
                this.uuidToFlattenedDictMap,
                this.fieldNameToSaltMap,
                this.hashAlgorithm);
    }

    @SerialisationTestConstructor
    private HashedReceiveDataRules() {
        this(0L,
                ReceiveDataRules.builder().uuid(UUID.randomUUID().toString()).build(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                HashAlgorithm.BCRYPT);
    }

    public HashedReceiveDataRules(final ReceiveDataRules receiveDataRules,
                                  final Map<String, DictionaryDoc> uuidToFlattenedDictMap,
                                  final Map<String, String> fieldNameToSaltMap,
                                  final HashAlgorithm hashAlgorithm) {
        this(System.currentTimeMillis(),
                receiveDataRules,
                uuidToFlattenedDictMap,
                fieldNameToSaltMap,
                hashAlgorithm);
    }

    @JsonPropertyDescription("The time that this snapshot of the rules was taken.")
    public long getSnapshotTimeEpochMs() {
        return snapshotTimeEpochMs;
    }

    @JsonPropertyDescription("The rule set with only enable rules and fields that are used in those rules.")
    public ReceiveDataRules getReceiveDataRules() {
        return receiveDataRules;
    }

    @JsonPropertyDescription("A map of dictionary UUIDs to the dictionary corresponding to that UUID. " +
                             "Each dictionary has had any imports merged into it and the imports removed to make " +
                             "a flat hierarchy. Dictionaries used in terms with a field that requires obfuscation " +
                             "have had their content obfuscated.")
    public Map<String, DictionaryDoc> getUuidToFlattenedDictMap() {
        return uuidToFlattenedDictMap;
    }

    /**
     * @return A {@link Map} of field name (lower-case) to the salt used to hash values for
     * that field.
     */
    @JsonPropertyDescription("Map of field name (lower-case) to the salt used to obfuscate value(s) for " +
                             "that field. This map will only contain field keys for those fields that require" +
                             "obfuscation. If not fields are obfuscated, it will be empty.")
    public Map<String, String> getFieldNameToSaltMap() {
        return fieldNameToSaltMap;
    }

    @JsonPropertyDescription("The hash algorithm used to obfuscated values. If no fields are obfuscated then " +
                             "this will be null.")
    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithm;
    }

    @JsonIgnore
    public List<ReceiveDataRule> getRules() {
        return NullSafe.list(receiveDataRules.getRules());
    }

    @JsonIgnore
    public List<QueryField> getFields() {
        return NullSafe.list(receiveDataRules.getFields());
    }

    public String getSalt(final String fieldName) {
        return NullSafe.get(fieldName, fieldNameToSaltMap::get);
    }

    /**
     * Set of field names whose values need to be compared in hashed form.
     * Hashed fields will only support certain conditions, i.e. case-sense equals,
     * case-sense not equals, in, in dictionary.
     */
    @JsonIgnore
    public Set<String> getHashedFieldNames() {
        return fieldNameToSaltMap.keySet();
    }

    public boolean isHashedFieldName(final String fieldName) {
        return fieldName != null
               && fieldNameToSaltMap.containsKey(fieldName);
    }

    public DictionaryDoc getFlattenedDictionary(final DocRef docRef) {
        return NullSafe.get(
                docRef,
                DocRef::getUuid,
                uuidToFlattenedDictMap::get);
    }

    private void validateValues(final ReceiveDataRules receiveDataRules,
                                final Map<String, DictionaryDoc> dictionaries,
                                final Map<String, String> fieldNameToSaltMap,
                                final HashAlgorithm hashAlgorithm) {
        if (!getHashedFieldNames().isEmpty()) {
            Objects.requireNonNull(hashAlgorithm,
                    "hashAlgorithm must be set if at least one field is hashed");
        }

        if (!fieldNameToSaltMap.keySet().stream().allMatch(field -> field.equals(field.toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("fields in fieldNameToSaltMap must all be lower case.");
        }

        final List<QueryField> fields = NullSafe.list(receiveDataRules.getFields());
        final Set<String> fieldNames = fields.stream()
                .map(QueryField::getFldName)
                .collect(Collectors.toSet());

        final Set<String> dictUuids = dictionaries.keySet();

        final List<ReceiveDataRule> rules = NullSafe.list(receiveDataRules.getRules());
        for (final ReceiveDataRule rule : rules) {
            final ExpressionOperator expression = rule.getExpression();
            //noinspection SimplifyStreamApiCallChains // Cos GWT
            final List<String> fieldsInRule = ExpressionUtil.fields(expression).stream()
                    .map(HashedReceiveDataRules::stripHashedSuffix)
                    .collect(Collectors.toList());
            for (final String fieldInRule : fieldsInRule) {
                if (!fieldNames.contains(fieldInRule)) {
                    throw new IllegalArgumentException(
                            "Found field '"
                            + fieldInRule + "' in rule " + rule.getRuleNumber()
                            + " but not in fields list");
                }
            }
            //noinspection SimplifyStreamApiCallChains // Cos GWT
            final List<DocRef> dictsInRule = ExpressionUtil.terms(expression, null)
                    .stream()
                    .filter(term -> term.hasCondition(Condition.IN_DICTIONARY))
                    .map(ExpressionTerm::getDocRef)
                    .collect(Collectors.toList());
            for (final DocRef dictInRule : dictsInRule) {
                if (!dictUuids.contains(dictInRule.getUuid())) {
                    throw new IllegalArgumentException(
                            "Found dictionary '"
                            + dictInRule + "' in rule " + rule.getRuleNumber()
                            + " but not in dictionaries list");
                }
            }
        }
    }

    @Override
    public String toString() {
        return "HashedReceiveDataRules{" +
               "snapshotTimeEpochMs=" + snapshotTimeEpochMs +
               ", receiveDataRules=" + receiveDataRules +
               ", uuidToFlattenedDictMap=" + uuidToFlattenedDictMap +
               ", fieldNameToSaltMap=" + fieldNameToSaltMap +
               ", hashAlgorithm=" + hashAlgorithm +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final HashedReceiveDataRules that = (HashedReceiveDataRules) object;
        return snapshotTimeEpochMs == that.snapshotTimeEpochMs
               && Objects.equals(receiveDataRules, that.receiveDataRules)
               && Objects.equals(uuidToFlattenedDictMap, that.uuidToFlattenedDictMap)
               && Objects.equals(fieldNameToSaltMap, that.fieldNameToSaltMap)
               && hashAlgorithm == that.hashAlgorithm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                snapshotTimeEpochMs,
                receiveDataRules,
                uuidToFlattenedDictMap,
                fieldNameToSaltMap,
                hashAlgorithm);
    }

    public static boolean isHashedField(final String fieldName) {
        return NullSafe.test(fieldName, name -> name.endsWith(HASHED_FIELD_NAME_SUFFIX));
    }

    public static String markFieldAsHashed(final String fieldName) {
        return Objects.requireNonNull(fieldName) + HASHED_FIELD_NAME_SUFFIX;
    }

    public static String stripHashedSuffix(final String fieldName) {
        Objects.requireNonNull(fieldName);
        if (fieldName.endsWith(HASHED_FIELD_NAME_SUFFIX)) {
            return fieldName.substring(0, fieldName.length() - HASHED_FIELD_NAME_SUFFIX.length());
        } else {
            return fieldName;
        }
    }
}
