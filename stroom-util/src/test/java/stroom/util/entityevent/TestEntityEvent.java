/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.entityevent;

import stroom.docref.DocRef;
import stroom.test.common.TestUtil;
import stroom.util.entityevent.EntityEvent.EntityEventData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;

class TestEntityEvent {

    @Test
    void testSerde1() {
        final MyEntityEventData myEntityEventData = new MyEntityEventData("foo", true);

        final EntityEvent entityEvent = new EntityEvent(DocRef.builder()
                .randomUuid()
                .type("myDocRef")
                .build(),
                null,
                EntityAction.CREATE,
                myEntityEventData);

        final EntityEvent entityEvent2 = TestUtil.testSerialisation(entityEvent, EntityEvent.class);
        final MyEntityEventData myEntityEventData2 = entityEvent2.getDataAsObject(MyEntityEventData.class);

        Assertions.assertThat(myEntityEventData2)
                .isEqualTo(myEntityEventData);
    }

    @Test
    void testSerde2() {
        final EntityEvent entityEvent = new EntityEvent(DocRef.builder()
                .randomUuid()
                .type("myDocRef")
                .build(),
                null,
                EntityAction.CREATE);

        TestUtil.testSerialisation(entityEvent, EntityEvent.class);
    }


    // --------------------------------------------------------------------------------


    private static class MyEntityEventData implements EntityEventData {

        @JsonProperty
        final String str;
        @JsonProperty
        final boolean aBool;

        @JsonCreator
        private MyEntityEventData(@JsonProperty("str") final String str,
                                  @JsonProperty("aBool") final boolean aBool) {
            this.str = str;
            this.aBool = aBool;
        }

        public String getStr() {
            return str;
        }

        public boolean isaBool() {
            return aBool;
        }

        @Override
        public String toString() {
            return "MyEntityEventData{" +
                   "str='" + str + '\'' +
                   ", aBool=" + aBool +
                   '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MyEntityEventData that = (MyEntityEventData) o;
            return aBool == that.aBool
                   && Objects.equals(str, that.str);
        }

        @Override
        public int hashCode() {
            return Objects.hash(str, aBool);
        }
    }
}
