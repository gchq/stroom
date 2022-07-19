package stroom.statistics.impl.sql;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestSQLStatisticAggregationTransactionHelper {

    @Test
    void condenseSql_condensed() {
        final String input = "   HELLO     WORLD THIS    IS     ME     ";
        final String output = SQLStatisticAggregationTransactionHelper.condenseSql(input);
        Assertions.assertThat(output)
                .isEqualTo("HELLO WORLD THIS IS ME");
    }

    @Test
    void condenseSql_unchanged() {
        final String input = "HELLO WORLD THIS IS ME";
        final String output = SQLStatisticAggregationTransactionHelper.condenseSql(input);
        Assertions.assertThat(output)
                .isEqualTo(input);
    }
}
