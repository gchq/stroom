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

package stroom.util.spring;

import org.springframework.context.ApplicationContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

public class ApplicationContextUtil {
    public static final List<String> getBeanNamesWithAnnotation(final ApplicationContext applicationContext,
            final Class<? extends Annotation> annotationType) {
        final List<String> rtnList = new ArrayList<String>();
        final String[] allBeans = applicationContext.getBeanDefinitionNames();

        for (final String beanName : allBeans) {
            if (applicationContext.findAnnotationOnBean(beanName, annotationType) != null) {
                rtnList.add(beanName);
            }
        }

        return rtnList;
    }
}
