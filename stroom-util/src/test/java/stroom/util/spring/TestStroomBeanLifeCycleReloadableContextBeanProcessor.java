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

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import stroom.util.test.ComponentTest;

/**
 * This test class does not use a @Configuration because it needs to create
 * multiple contexts manually.
 */
@Category(ComponentTest.class)
public class TestStroomBeanLifeCycleReloadableContextBeanProcessor {
    @Test
    public void testStartAndStop() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                StroomBeanLifeCycleTestConfiguration.class)) {
            MockStroomBeanLifeCycleBean bean1 = (MockStroomBeanLifeCycleBean) context.getBean("bean1");
            MockStroomBeanLifeCycleBean bean2 = (MockStroomBeanLifeCycleBean) context.getBean("bean2");

            Assert.assertTrue(bean1.isRunning());
            Assert.assertTrue(bean2.isRunning());

            context.start();
            context.stop();
            context.destroy();

            Assert.assertFalse(bean1.isRunning());
            Assert.assertFalse(bean2.isRunning());
        }
    }

    @Test
    public void testStartAndStopWithTwoContexts() {
        try (AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext(
                StroomBeanLifeCycleTestConfiguration.class);
             AnnotationConfigApplicationContext context2 = new AnnotationConfigApplicationContext(
                     StroomBeanLifeCycleTestConfiguration.class)) {
            MockStroomBeanLifeCycleBean context1Bean1 = (MockStroomBeanLifeCycleBean) context1.getBean("bean1");
            MockStroomBeanLifeCycleBean context1Bean2 = (MockStroomBeanLifeCycleBean) context1.getBean("bean2");

            MockStroomBeanLifeCycleBean context2Bean1 = (MockStroomBeanLifeCycleBean) context2.getBean("bean1");
            MockStroomBeanLifeCycleBean context2Bean2 = (MockStroomBeanLifeCycleBean) context2.getBean("bean2");

            Assert.assertTrue(context1Bean1.isRunning());
            Assert.assertTrue(context1Bean2.isRunning());

            Assert.assertFalse(context2Bean1.isRunning());
            Assert.assertFalse(context2Bean2.isRunning());

            context1.stop();
            context1.destroy();

            Assert.assertFalse(context1Bean1.isRunning());
            Assert.assertFalse(context1Bean2.isRunning());

            Assert.assertTrue(context2Bean1.isRunning());
            Assert.assertTrue(context2Bean2.isRunning());

            context2.stop();
            context2.destroy();

            Assert.assertFalse(context1Bean1.isRunning());
            Assert.assertFalse(context1Bean2.isRunning());

            Assert.assertFalse(context2Bean1.isRunning());
            Assert.assertFalse(context2Bean2.isRunning());
        }
    }

}
