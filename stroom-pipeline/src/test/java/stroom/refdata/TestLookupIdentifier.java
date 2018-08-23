/*
 * Copyright 2018 Crown Copyright
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

package stroom.refdata;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class TestLookupIdentifier {

    @Test
    public void isMapNested_true() {

        String map = "map1" + LookupIdentifier.NEST_SEPARATOR +
                "map2" + LookupIdentifier.NEST_SEPARATOR +
                "map3";

        LookupIdentifier identifier = new LookupIdentifier(map, "key1", 12345);
        assertThat(identifier.isMapNested()).isTrue();
    }

    @Test
    public void isMapNested_false() {

        LookupIdentifier identifier = new LookupIdentifier("map1", "key1", 12345);
        assertThat(identifier.isMapNested()).isFalse();
    }

    @Test(expected = RuntimeException.class)
    public void isMapNested_badlyFormatted1() {

        String map = "map1" + LookupIdentifier.NEST_SEPARATOR;

        LookupIdentifier identifier = new LookupIdentifier(map, "key1", 12345);
    }

    @Test(expected = RuntimeException.class)
    public void isMapNested_badlyFormatted2() {

        String map =  LookupIdentifier.NEST_SEPARATOR + "map1";

        LookupIdentifier identifier = new LookupIdentifier(map, "key1", 12345);
    }

    @Test
    public void getPrimaryMapName_nested() {
        String map = "map1" + LookupIdentifier.NEST_SEPARATOR +
                "map2" + LookupIdentifier.NEST_SEPARATOR +
                "map3";

        LookupIdentifier identifier = new LookupIdentifier(map, "key1", 12345);
        assertThat(identifier.getPrimaryMapName()).isEqualTo("map1");
        assertThat(identifier.getPrimaryMapName()).isNotEqualTo(identifier.getMap());
    }

    @Test
    public void getPrimaryMapName_notNested() {
        LookupIdentifier identifier = new LookupIdentifier("map1", "key1", 12345);
        String primaryMapName = identifier.getPrimaryMapName();
        assertThat(identifier.getPrimaryMapName()).isEqualTo(identifier.getMap());
    }

    @Test
    public void getNestedLookupIdentifier() {
        String map = "map1" + LookupIdentifier.NEST_SEPARATOR +
                "map2" + LookupIdentifier.NEST_SEPARATOR +
                "map3";

        LookupIdentifier identifier = new LookupIdentifier(map, "key1", 12345);
        LookupIdentifier nestedIdentifier = identifier.getNestedLookupIdentifier("key2");
        assertThat(nestedIdentifier.isMapNested()).isTrue();
        assertThat(nestedIdentifier.getPrimaryMapName()).isEqualTo("map2");
        assertThat(nestedIdentifier.getMap()).isEqualTo("map2" + LookupIdentifier.NEST_SEPARATOR + "map3");
        assertThat(nestedIdentifier.getKey()).isEqualTo("key2");

        LookupIdentifier secondNestedIdentifier = nestedIdentifier.getNestedLookupIdentifier("key3");
        assertThat(secondNestedIdentifier.isMapNested()).isFalse();
        assertThat(secondNestedIdentifier.getMap()).isEqualTo("map3");
    }
}