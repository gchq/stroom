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

package stroom.entity.shared;

import org.mockito.Matchers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

public class MockitoEntityServiceUtil {
    public static <E extends Entity, T extends EntityService<E>> void apply(final T entityService) {
        Mockito.when(entityService.save(Matchers.any())).then(i -> i.getArguments()[0]);
        Mockito.when(entityService.load(Matchers.any())).then(i -> i.getArguments()[0]);
    }

    public static <E extends Entity, C extends BaseCriteria, T extends FindService<E, C>> void applySingleFind(final T findService,
            final E result) {
        final List<E> lst = new ArrayList<>();
        lst.add(result);
        Mockito.when(findService.find(Matchers.any())).thenReturn(new BaseResultList<>(lst, 0L, 1L, false));
    }

    public static <E extends Entity, C extends BaseCriteria, T extends FindService<E, C>> void applyEmptyFind(final T findService) {
        final List<E> lst = new ArrayList<>();
        Mockito.when(findService.find(Matchers.any())).thenReturn(new BaseResultList<>(lst, 0L, 0L, false));
    }
}
