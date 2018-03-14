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

package stroom.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.entity.DocumentPermissionCache;
import stroom.security.SecurityContext;
import stroom.util.cache.CacheManager;
import stroom.guice.StroomBeanStore;
import stroom.xml.converter.ds3.DS3ParserFactory;
import stroom.xmlschema.XMLSchemaCache;

import javax.inject.Provider;
import javax.xml.transform.URIResolver;

@Configuration
public class PipelineCacheSpringConfig {
    @Bean
    public DSChooser dSChooser(final Provider<DS3ParserFactory> parserFactoryProvider) {
        return new DSChooser(parserFactoryProvider);
    }

    @Bean
    public ParserFactoryPool parserFactoryPool(final CacheManager cacheManager,
                                               final DocumentPermissionCache documentPermissionCache,
                                               final SecurityContext securityContext,
                                               final DSChooser dsChooser) {
        return new ParserFactoryPoolImpl(cacheManager, documentPermissionCache, securityContext, dsChooser);
    }

    @Bean
    public SchemaLoader schemaLoader(final XMLSchemaCache xmlSchemaCache) {
        return new SchemaLoaderImpl(xmlSchemaCache);
    }

    @Bean
    public SchemaPool schemaPool(final CacheManager cacheManager,
                                 final SchemaLoader schemaLoader,
                                 final XMLSchemaCache xmlSchemaCache,
                                 final SecurityContext securityContext) {
        return new SchemaPoolImpl(cacheManager, schemaLoader, xmlSchemaCache, securityContext);
    }

    @Bean
    public XSLTPool xSLTPool(final CacheManager cacheManager,
                             final DocumentPermissionCache documentPermissionCache,
                             final SecurityContext securityContext,
                             final URIResolver uriResolver,
                             final StroomBeanStore beanStore) {
        return new XSLTPoolImpl(cacheManager, documentPermissionCache, securityContext, uriResolver, beanStore);
    }
}