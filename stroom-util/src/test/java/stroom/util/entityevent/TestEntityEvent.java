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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        final EntityEvent event2 = TestUtil.testSerialisation(entityEvent, EntityEvent.class);
        final MyEntityEventData myEntityEventData2 = event2.getDataObject(MyEntityEventData.class);

        assertThat(myEntityEventData2)
                .isEqualTo(myEntityEventData);

        assertThatThrownBy(event2::getStringData)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(event2::getLongData)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(event2::getIntData)
                .isInstanceOf(IllegalArgumentException.class);
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
        assertThatThrownBy(event2::getLongData)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(event2::getIntData)
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event2.getDataAsJson())
                .isEqualTo("foo");
    }

    @Test
    void testSerde4() {
        final DocRef docRef = DocRef.builder()
                .randomUuid()
                .type("myDocRef")
                .build();
        final EntityEvent entityEvent = new EntityEvent(
                docRef,
                null,
                EntityAction.CREATE,
                Long.class.getName(),
                "1234");

        final EntityEvent event2 = TestUtil.testSerialisation(entityEvent, EntityEvent.class);
        assertThat(event2.getLongData())
                .isEqualTo(1234L);
        assertThatThrownBy(event2::getStringData)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(event2::getIntData)
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event2.getDataAsJson())
                .isEqualTo("1234");
    }

    @Test
    void testSerde5() {
        final DocRef docRef = DocRef.builder()
                .randomUuid()
                .type("myDocRef")
                .build();
        final EntityEvent entityEvent = new EntityEvent(
                docRef,
                null,
                EntityAction.CREATE,
                Integer.class.getName(),
                "1234");

        final EntityEvent event2 = TestUtil.testSerialisation(entityEvent, EntityEvent.class);
        assertThat(event2.getIntData())
                .isEqualTo(1234);
        assertThatThrownBy(event2::getStringData)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(event2::getLongData)
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event2.getDataAsJson())
                .isEqualTo("1234");
    }

    @Test
    void testBuildFiringWithoutData() {
        final EntityEventBus eventBus = Mockito.mock(EntityEventBus.class);
        final DocRef docRef = DocRef.builder().type("myDocRef").randomUuid().build();

        EntityEvent.buildFiring(eventBus)
                .withDocRef(docRef)
                .withAction(EntityAction.CREATE)
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
                .withDocRef(docRef)
                .withOldDocRef(oldDocRef)
                .withAction(EntityAction.UPDATE)
                .withJsonData(MyEntityEventData.class.getName(), "{\"str\":\"foo\",\"aBool\":true}")
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
                .withDocRef(docRef)
                .withAction(EntityAction.CREATE)
                .withData(eventData)
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
                        .withDocRef(docRef)
                        .withAction(null)
                        .fire())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action");
        Mockito.verifyNoInteractions(eventBus);
    }

    @Test
    void testBuildFiringWithStringData() {
        final EntityEventBus eventBus = Mockito.mock(EntityEventBus.class);
        final DocRef docRef = DocRef.builder()
                .type("myDocRef")
                .randomUuid()
                .build();

        EntityEvent.buildFiring(eventBus)
                .withDocRef(docRef)
                .withAction(EntityAction.CREATE)
                .withStringData("foo")
                .fire();

        final ArgumentCaptor<EntityEvent> captor = ArgumentCaptor.forClass(EntityEvent.class);
        Mockito.verify(eventBus)
                .fire(captor.capture());
        final EntityEvent firedEvent = captor.getValue();
        assertThat(firedEvent)
                .isEqualTo(new EntityEvent(
                        docRef,
                        null,
                        EntityAction.CREATE,
                        String.class.getName(),
                        "foo"));
        assertThat(firedEvent.getStringData())
                .isEqualTo("foo");
        assertThat(firedEvent.getDataClassName())
                .isEqualTo(String.class.getName());
        assertThat(firedEvent.getDataAsJson())
                .isEqualTo("foo");
    }

    @Test
    void testBuildFiringWithLongData() {
        final EntityEventBus eventBus = Mockito.mock(EntityEventBus.class);
        final DocRef docRef = DocRef.builder()
                .type("myDocRef")
                .randomUuid()
                .build();

        EntityEvent.buildFiring(eventBus)
                .withDocRef(docRef)
                .withAction(EntityAction.CREATE)
                .withLongData(1234L)
                .fire();

        final ArgumentCaptor<EntityEvent> captor = ArgumentCaptor.forClass(EntityEvent.class);
        Mockito.verify(eventBus)
                .fire(captor.capture());
        final EntityEvent firedEvent = captor.getValue();
        assertThat(firedEvent)
                .isEqualTo(new EntityEvent(
                        docRef,
                        null,
                        EntityAction.CREATE,
                        Long.class.getName(),
                        "1234"));
        assertThat(firedEvent.getLongData())
                .isEqualTo(1234L);
        assertThat(firedEvent.getDataClassName())
                .isEqualTo(Long.class.getName());
        assertThat(firedEvent.getDataAsJson())
                .isEqualTo("1234");
    }

    @Test
    void testBuildFiringWithIntData() {
        final EntityEventBus eventBus = Mockito.mock(EntityEventBus.class);
        final DocRef docRef = DocRef.builder()
                .type("myDocRef")
                .randomUuid()
                .build();

        EntityEvent.buildFiring(eventBus)
                .withDocRef(docRef)
                .withAction(EntityAction.CREATE)
                .withIntData(1234)
                .fire();

        final ArgumentCaptor<EntityEvent> captor = ArgumentCaptor.forClass(EntityEvent.class);
        Mockito.verify(eventBus)
                .fire(captor.capture());
        final EntityEvent firedEvent = captor.getValue();
        assertThat(firedEvent)
                .isEqualTo(new EntityEvent(
                        docRef,
                        null,
                        EntityAction.CREATE,
                        Integer.class.getName(),
                        "1234"));
        assertThat(firedEvent.getIntData())
                .isEqualTo(1234);
        assertThat(firedEvent.getDataClassName())
                .isEqualTo(Integer.class.getName());
        assertThat(firedEvent.getDataAsJson())
                .isEqualTo("1234");
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
