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
 */

package stroom.startup;

import io.dropwizard.setup.Environment;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import stroom.Config;

public class SpringContexts {

    final AnnotationConfigWebApplicationContext applicationContext;
    final AnnotationConfigWebApplicationContext rootContext;

    public SpringContexts() throws ClassNotFoundException {
        rootContext = new AnnotationConfigWebApplicationContext();

        applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.setParent(rootContext);
        applicationContext.registerShutdownHook();
        applicationContext.register(CommonAnnotationBeanPostProcessor.class);
        // We don't need to register @Configuration classes here because they're loaded in SpringContexts.newUpgradeDispatcherServlet(...)
    }

    public void start(Environment environment, Config configuration) {
        // We need to set the servlet context otherwise there will be no default servlet handling.
        applicationContext.setServletContext(environment.getApplicationContext().getServletContext());

        rootContext.refresh();
        rootContext.start();
        applicationContext.refresh();
        applicationContext.getBeanFactory().registerSingleton("dwConfiguration", configuration);
        applicationContext.getBeanFactory().registerSingleton("dwEnvironment", environment);
        applicationContext.getBeanFactory().registerSingleton("dwObjectMapper", environment.getObjectMapper());
        applicationContext.start();
    }
}
