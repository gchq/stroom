/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.script.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.script.shared.Script;
import stroom.test.AbstractCoreIntegrationTest;

import javax.annotation.Resource;

public class TestScriptServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private ScriptService scriptService;

    @Test
    public void testUTF8Resource() {
        final String data = "var π = Math.PI, τ = 2 * π, halfπ = π / 2, ε = 1e-6, ε2 = ε * ε, d3_radians = π / 180, d3_degrees = 180 / π;";

        final Script script = scriptService.create("test");
        script.setResource(data);
        scriptService.save(script);
        final Script loaded = scriptService.load(script);

        Assert.assertEquals(data, loaded.getResource());
    }
}
