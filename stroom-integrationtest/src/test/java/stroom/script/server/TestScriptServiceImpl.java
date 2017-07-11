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

package stroom.script.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.entity.shared.Res;
import stroom.script.shared.Script;
import stroom.script.shared.ScriptService;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Set;

public class TestScriptServiceImpl extends AbstractCoreIntegrationTest {
    private static final Set<String> FETCH_SET = Collections.singleton(Script.FETCH_RESOURCE);

    @Resource
    private ScriptService scriptService;

    @Test
    public void testUTF8Resource() {
        final String data = "var π = Math.PI, τ = 2 * π, halfπ = π / 2, ε = 1e-6, ε2 = ε * ε, d3_radians = π / 180, d3_degrees = 180 / π;";

        final Res res = new Res();
        res.setData(data);

        final Script script = scriptService.create(null, "test");
        script.setResource(res);
        scriptService.save(script);
        final Script loaded = scriptService.loadByUuid(script.getUuid(), FETCH_SET);

        Assert.assertEquals(data, loaded.getResource().getData());
    }
}
