package stroom.receive.rules.shared;

import stroom.datasource.api.v2.QueryField;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
import stroom.security.shared.HashAlgorithm;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JsonPropertyOrder(alphabetic = true)
public class HashedReceiveDataRules {

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
     * Field names all in lower-case
     */
    @JsonProperty
    private final Map<String, String> fieldNameToSaltMap;
    /**
     * The hash algorithm used to hash the hashed field values.
     */
    @JsonProperty
    private final HashAlgorithm hashAlgorithm;

    /**
     * @param receiveDataRules
     * @param uuidToFlattenedDictMap
     * @param fieldNameToSaltMap     A {@link Map} of field name (lower-case) to the salt used to hash values for
     *                               that field.
     * @param hashAlgorithm
     */
    @JsonCreator
    public HashedReceiveDataRules(
            @JsonProperty("receiveDataRules") final ReceiveDataRules receiveDataRules,
            @JsonProperty("uuidToFlattenedDictMap") final Map<String, DictionaryDoc> uuidToFlattenedDictMap,
            @JsonProperty("fieldNameToSaltMap") final Map<String, String> fieldNameToSaltMap,
            @JsonProperty("hashAlgorithm") final HashAlgorithm hashAlgorithm) {

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
            final List<String> fieldsInRule = ExpressionUtil.fields(expression);
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

    public ReceiveDataRules getReceiveDataRules() {
        return receiveDataRules;
    }

    @JsonIgnore
    public List<ReceiveDataRule> getRules() {
        return NullSafe.list(receiveDataRules.getRules());
    }

    @JsonIgnore
    public List<QueryField> getFields() {
        return NullSafe.list(receiveDataRules.getFields());
    }

    public Map<String, DictionaryDoc> getUuidToFlattenedDictMap() {
        return uuidToFlattenedDictMap;
    }

    @JsonIgnore
    public DictionaryDoc getFlattenedDictionary(final DocRef docRef) {
        return NullSafe.get(
                docRef,
                DocRef::getUuid,
                uuidToFlattenedDictMap::get);
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

    @JsonIgnore
    public boolean isHashedFieldName(final String fieldName) {
        return fieldName != null
               && fieldNameToSaltMap.containsKey(fieldName);
    }

    /**
     * @return A {@link Map} of field name (lower-case) to the salt used to hash values for
     * that field.
     */
    public Map<String, String> getFieldNameToSaltMap() {
        return fieldNameToSaltMap;
    }

    @JsonIgnore
    public String getSalt(final String fieldName) {
        return NullSafe.get(fieldName, fieldNameToSaltMap::get);
    }

    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithm;
    }

    @Override
    public String toString() {
        return "HashedReceiveDataRules{" +
               "receiveDataRules=" + receiveDataRules +
               ", dictionaries=" + uuidToFlattenedDictMap +
               ", fieldNameToSaltMap=" + fieldNameToSaltMap +
               ", hashAlgorithm=" + hashAlgorithm +
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
        final HashedReceiveDataRules that = (HashedReceiveDataRules) o;
        return Objects.equals(receiveDataRules, that.receiveDataRules)
               && Objects.equals(uuidToFlattenedDictMap, that.uuidToFlattenedDictMap)
               && Objects.equals(fieldNameToSaltMap, that.fieldNameToSaltMap)
               && hashAlgorithm == that.hashAlgorithm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiveDataRules, uuidToFlattenedDictMap, fieldNameToSaltMap, hashAlgorithm);
    }
}
