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

package stroom.util.config;

import org.junit.Assert;
import org.junit.Test;

public class TestStroomProperties {
    @Test
    public void testOverrideProperty() {
        final String key = "TestProperties";
        final String oldValue = key + "-value";
        final String newValue = key + "-newValue";

        StroomProperties.setProperty(key, oldValue, StroomProperties.Source.TEST);
        Assert.assertEquals(oldValue, StroomProperties.getProperty(key));

        StroomProperties.setOverrideProperty(key, newValue, StroomProperties.Source.TEST);

        Assert.assertEquals(newValue, StroomProperties.getProperty(key));

        StroomProperties.removeOverrides();

        Assert.assertEquals(oldValue, StroomProperties.getProperty(key));
    }
}
