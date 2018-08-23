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
 */

package stroom.entity.event;

import com.google.inject.AbstractModule;

public class EntityEventModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(EntityEventBus.class).to(EntityEventBusImpl.class);
    }

//
// TODO: @66 DON'T THINK THIS IS NEEDED ANYMORE SO DELETE IT
//    @Bean
//    public GenericEntityMarshaller genericEntityMarshaller() {
//        return new GenericEntityMarshallerImpl();
//    }
//

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}