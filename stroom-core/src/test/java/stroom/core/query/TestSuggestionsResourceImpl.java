package stroom.core.query;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestSuggestionsResourceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSuggestionsResourceImpl.class);

    @Test
    void fuzzyMatcher() {
        // Ones that match
        doFuzzyMatchTest("THIS_", "THIS_IS_MY_FEED", true);
        doFuzzyMatchTest("MY_FEED", "THIS_IS_MY_FEED", true);
        doFuzzyMatchTest("T_I_M_F", "THIS_IS_MY_FEED", true);
        doFuzzyMatchTest("T_I_M_F", "THIS_IS_MF_FEED", true);
        doFuzzyMatchTest("TH_IS_MY_FE", "THIS_IS_MY_FEED", true);
        doFuzzyMatchTest("T___F", "THIS_IS_MY_FEED", true);
        doFuzzyMatchTest("TIMF", "THIS_IS_MY_FEED", true);
        doFuzzyMatchTest("TIISMYFE", "THIS_IS_MY_FEED", true);

        doFuzzyMatchTest("99", "THIS_IS_MY_99_FEED", true);
        doFuzzyMatchTest("_99_", "THIS_IS_MY_99_FEED", true);
        doFuzzyMatchTest("99_FEED", "THIS_IS_MY_99_FEED", true);
        doFuzzyMatchTest("T_I_M_99_F", "THIS_IS_MY_99_FEED", true);

        // Ones that don't
        doFuzzyMatchTest("T_X__F", "THIS_IS_MY_FEED", false);

    }

    private void doFuzzyMatchTest(final String input, final String text, final boolean shouldMatch) {
        LOGGER.info("-----------------------------------------------------");
        LOGGER.debug("Testing input: {} against text: {}, shouldMatch: {}", input, text, shouldMatch);
        boolean actualResult = SuggestionsResourceImpl.isFuzzyMatch(input, text);
        Assertions.assertThat(actualResult).isEqualTo(shouldMatch);
        LOGGER.info("-----------------------------------------------------");
    }
}