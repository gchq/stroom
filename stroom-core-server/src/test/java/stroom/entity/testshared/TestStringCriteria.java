/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.testshared;

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.StringCriteria;
import stroom.entity.shared.StringCriteria.MatchStyle;
import stroom.util.test.StroomUnitTest;

public class TestStringCriteria extends StroomUnitTest {
    @Test
    public void testSimple() {
        final StringCriteria criteria = new StringCriteria();
        Assert.assertFalse(criteria.isConstrained());
        criteria.setString("");
        Assert.assertTrue(criteria.isConstrained());
        criteria.setMatchStyle(MatchStyle.WildEnd);
        Assert.assertFalse(criteria.isConstrained());
        criteria.setString("X");
        Assert.assertTrue(criteria.isConstrained());

    }

    @Test
    public void testIsMatch() {
        final StringCriteria criteria = new StringCriteria();
        criteria.setString("XYZ");

        Assert.assertFalse(criteria.isMatch("XY"));
        Assert.assertTrue(criteria.isMatch("XYZ"));

        criteria.setMatchStyle(MatchStyle.WildEnd);

        Assert.assertTrue(criteria.isMatch("XYZ123"));
        Assert.assertTrue(criteria.isMatch("XYZ"));
        Assert.assertFalse(criteria.isMatch("123XYZ123"));

        criteria.setMatchStyle(MatchStyle.WildStandAndEnd);

        Assert.assertTrue(criteria.isMatch("XYZ123"));
        Assert.assertTrue(criteria.isMatch("123XYZ123"));

    }

}
