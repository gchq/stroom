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

import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.servlet.ServletContext;

//TODO 2017-02-25 - Dropwizard migration: this is currently redundant
public class MyContextLoaderListener extends ContextLoaderListener {

    private AnnotationConfigWebApplicationContext applicationContext;

    public MyContextLoaderListener(AnnotationConfigWebApplicationContext applicationContext){
        this.applicationContext = applicationContext;
    }

    @Override
    protected WebApplicationContext createWebApplicationContext(ServletContext sc) {
        sc.setInitParameter("contextClass",
                "org.springframework.web.context.support.AnnotationConfigWebApplicationContext");
        applicationContext.setServletContext(sc);
        return super.createWebApplicationContext(sc);
    }

//    @Override
//    public void contextInitialized(final ServletContextEvent event) {
//        new ThreadScopeRunnable() {
//            @Override
//            protected void exec() {
//                MyContextLoaderListener.super.contextInitialized(event);
//            }
//        }.run();
//    }
}
