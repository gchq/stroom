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

package stroom.script;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.StroomEntityManager;
import stroom.importexport.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.spring.EntityManagerSupport;
import stroom.util.spring.StroomScope;

@Configuration
public class ScriptSpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchScriptHandler fetchScriptHandler(final ScriptService scriptService,
                                                 final SecurityContext securityContext) {
        return new FetchScriptHandler(scriptService, securityContext);
    }

    @Bean("scriptService")
    public ScriptService scriptService(final StroomEntityManager entityManager,
                                       final EntityManagerSupport entityManagerSupport,
                                       final ImportExportHelper importExportHelper,
                                       final SecurityContext securityContext) {
        return new ScriptServiceImpl(entityManager, entityManagerSupport, importExportHelper, securityContext);
    }

    @Bean(ScriptServlet.BEAN_NAME)
    public ScriptServlet scriptServlet(final ScriptService scriptService,
                                       final SecurityContext securityContext) {
        return new ScriptServlet(scriptService, securityContext);
    }
}