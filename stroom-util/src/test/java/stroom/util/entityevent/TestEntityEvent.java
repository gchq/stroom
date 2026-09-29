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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class TestEntityEvent {

    @Test
    void testSerde1() {
        final MyEntityEventData myEntityEventData = new MyEntityEventData("foo", true);

        final DocRef docRef = DocRef.builder()
                .randomUuid()
                .type("myDocRef")
                .build();
        final EntityEvent entityEvent = new EntityEvent(
                docRef,
                null,
                EntityAction.CREATE,
                myEntityEventData);

        final EntityEvent entityEvent2 = TestUtil.testSerialisation(entityEvent, EntityEvent.class);
        final MyEntityEventData myEntityEventData2 = entityEvent2.getDataObject(MyEntityEventData.class);

        assertThat(myEntityEventData2)
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

    @Test
    void testSerde3() {
        final DocRef docRef = DocRef.builder()
                .randomUuid()
                .type("myDocRef")
                .build();
        final EntityEvent entityEvent = new EntityEvent(
                docRef,
                null,
                EntityAction.CREATE,
                String.class.getName(),
                "foo");

        final EntityEvent event2 = TestUtil.testSerialisation(entityEvent, EntityEvent.class);
        assertThat(event2.getStringData())
                .isEqualTo("foo");
        assertThat(event2.getDataAsJson())
                .isEqualTo("foo");
    }

    @Test
    void testBuildFiringWithoutData() {
        final EntityEventBus eventBus = Mockito.mock(EntityEventBus.class);
        final DocRef docRef = DocRef.builder().type("myDocRef").randomUuid().build();

        EntityEvent.buildFiring(eventBus)
                .docRef(docRef)
                .action(EntityAction.CREATE)
                .fire();

        final ArgumentCaptor<EntityEvent> captor = ArgumentCaptor.forClass(EntityEvent.class);
        Mockito.verify(eventBus).fire(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new EntityEvent(docRef, EntityAction.CREATE));
    }

    @Test
    void testBuildFiringWithOldDocRefAndSerialisedData() {
        final EntityEventBus eventBus = Mockito.mock(EntityEventBus.class);
        final DocRef docRef = DocRef.builder().type("myDocRef").randomUuid().build();
        final DocRef oldDocRef = DocRef.builder().type("myDocRef").randomUuid().build();

        EntityEvent.buildFiring(eventBus)
                .docRef(docRef)
                .oldDocRef(oldDocRef)
                .action(EntityAction.UPDATE)
                .data(MyEntityEventData.class.getName(), "{\"str\":\"foo\",\"aBool\":true}")
                .fire();

        final ArgumentCaptor<EntityEvent> captor = ArgumentCaptor.forClass(EntityEvent.class);
        Mockito.verify(eventBus).fire(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new EntityEvent(docRef,
                        oldDocRef,
                        EntityAction.UPDATE,
                        MyEntityEventData.class.getName(),
                        "{\"str\":\"foo\",\"aBool\":true}"));
    }

    @Test
    void testBuildFiringWithEntityEventData() {
        final EntityEventBus eventBus = Mockito.mock(EntityEventBus.class);
        final DocRef docRef = DocRef.builder().type("myDocRef").randomUuid().build();
        final MyEntityEventData eventData = new MyEntityEventData("foo", true);

        EntityEvent.buildFiring(eventBus)
                .docRef(docRef)
                .action(EntityAction.CREATE)
                .data(eventData)
                .fire();

        final ArgumentCaptor<EntityEvent> captor = ArgumentCaptor.forClass(EntityEvent.class);
        Mockito.verify(eventBus).fire(captor.capture());
        assertThat(captor.getValue().getDataObject(MyEntityEventData.class))
                .isEqualTo(eventData);
    }

    @Test
    void testBuildFiringRequiresAction() {
        final EntityEventBus eventBus = Mockito.mock(EntityEventBus.class);
        final DocRef docRef = DocRef.builder().type("myDocRef").randomUuid().build();

        Assertions.assertThatThrownBy(() -> EntityEvent.buildFiring(eventBus)
                        .docRef(docRef)
                        .action(null)
                        .fire())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action");
        Mockito.verifyNoInteractions(eventBus);
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
