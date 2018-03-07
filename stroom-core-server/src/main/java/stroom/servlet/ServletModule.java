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

package stroom.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.importexport.ImportExportService;
import stroom.logging.StreamEventLog;
import stroom.node.NodeService;
import stroom.node.VolumeService;
import stroom.node.shared.ClientPropertiesService;
import stroom.properties.StroomPropertyService;
import stroom.resource.ResourceStore;
import stroom.security.SecurityContext;
import stroom.task.TaskHandler;
import stroom.task.TaskManager;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

import javax.inject.Named;

public class ServletModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(HttpServletRequestHolder.class).to(HttpServletRequestHolderImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.servlet.SessionListHandler.class);
    }
    //    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public DashboardServlet dashboardServlet(final StroomPropertyService stroomPropertyService) {
//        return new DashboardServlet(stroomPropertyService);
//    }
//
//    @Bean
//    public DebugServlet debugServlet() {
//        return new DebugServlet();
//    }
//
//    @Bean
//    public DynamicCSSServlet dynamicCSSServlet(final StroomPropertyService stroomPropertyService) {
//        return new DynamicCSSServlet(stroomPropertyService);
//    }
//
//    @Bean
//    public EchoServlet echoServlet() {
//        return new EchoServlet();
//    }
//
//    @Bean
//    public ExportConfigServlet exportConfigServlet(final ImportExportService importExportService,
//                                                   @Named("resourceStore") final ResourceStore resourceStore,
//                                                   final StroomPropertyService propertyService) {
//        return new ExportConfigServlet(importExportService, resourceStore, propertyService);
//    }
//
//    @Bean
//    public HttpServletRequestFilter httpServletRequestFilter(final HttpServletRequestHolder httpServletRequestHolder) {
//        return new HttpServletRequestFilter(httpServletRequestHolder);
//    }
//
//    @Bean
//    public HttpServletRequestHolder httpServletRequestHolder() {
//        return new HttpServletRequestHolderImpl();
//    }
//
//    @Bean
//    public ImportFileServlet importFileServlet(final ResourceStore resourceStore,
//                                               final StreamEventLog streamEventLog) {
//        return new ImportFileServlet(resourceStore, streamEventLog);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public SessionListHandler sessionListHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new SessionListHandler(dispatchHelper);
//    }
//
//    @Bean
//    public SessionListListener sessionListListener(final StroomBeanStore stroomBeanStore) {
//        return new SessionListListener(stroomBeanStore);
//    }
//
//    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public SessionListServlet sessionListServlet(final TaskManager taskManager) {
//        return new SessionListServletImpl(taskManager);
//    }
//
//    @Bean(SpringRequestFactoryServlet.BEAN_NAME)
//    public SpringRequestFactoryServlet springRequestFactoryServlet() {
//        return new SpringRequestFactoryServlet();
//    }
//
//    @Bean(StatusServletImpl.BEAN_NAME)
//    public StatusServlet statusServlet(final NodeService nodeService,
//                                       final ClientPropertiesService clientPropertiesService,
//                                       final VolumeService volumeService,
//                                       final SecurityContext securityContext) {
//        return new StatusServletImpl(nodeService, clientPropertiesService, volumeService, securityContext);
//    }
//
//    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public StroomServlet stroomServlet(final StroomPropertyService stroomPropertyService) {
//        return new StroomServlet(stroomPropertyService);
//    }
}