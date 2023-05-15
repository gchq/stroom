package stroom.test.common.data;

import java.util.List;

/**
 * @param fieldDefinitions TODO may be better to model this as a Map<Field, String>
 */
public record DataRecord(List<Field> fieldDefinitions, List<String> values) {

}
