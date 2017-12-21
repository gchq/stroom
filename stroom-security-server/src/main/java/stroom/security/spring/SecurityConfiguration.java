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

package stroom.security.spring;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import stroom.apiclients.AuthenticationServiceClients;
import stroom.security.server.JWTAuthenticationFilter;
import stroom.security.server.JWTService;
import stroom.security.server.NonceManager;

import javax.annotation.Resource;
import javax.servlet.Filter;
import java.util.Map;

/**
 * The authentication providers are configured manually because the method
 * signature of the
 *
 * @Override configure() method doesn't allow us to pass the @Components we need
 * to.
 */

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
@ComponentScan(basePackages = {"stroom.security.server"}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class SecurityConfiguration {
    public static final String PROD_SECURITY = "PROD_SECURITY";
    public static final String MOCK_SECURITY = "MOCK_SECURITY";

    @Resource
    private SecurityManager securityManager;

    @Bean(name = "jwtFilter")
    public JWTAuthenticationFilter jwtAuthenticationFilter(
            @Value("#{propertyConfigurer.getProperty('stroom.auth.service.url')}") final String authenticationServiceUrl,
            @Value("#{propertyConfigurer.getProperty('stroom.advertisedUrl')}") final String advertisedStroomUrl,
            JWTService jwtService,
            NonceManager nonceManager,
            AuthenticationServiceClients authenticationServiceClients) {
        return new JWTAuthenticationFilter(
                authenticationServiceUrl,
                advertisedStroomUrl,
                jwtService,
                nonceManager,
                authenticationServiceClients);
    }

    @Bean(name = "shiroFilter")
    public AbstractShiroFilter shiroFilter(final JWTAuthenticationFilter jwtAuthenticationFilter,
                                           @Value("#{propertyConfigurer.getProperty('stroom.auth.service.url')}") final String loginUrl) throws Exception {
        final ShiroFilterFactoryBean shiroFilter = new ShiroFilterFactoryBean();
        shiroFilter.setSecurityManager(securityManager);
        shiroFilter.setLoginUrl(loginUrl);
        shiroFilter.setSuccessUrl("/stroom.jsp");

        Map<String, Filter> filters = shiroFilter.getFilters();
        filters.put("jwtFilter", jwtAuthenticationFilter);

        shiroFilter.getFilterChainDefinitionMap().put("/**", "jwtFilter");
        shiroFilter.getFilterChainDefinitionMap().put("/api/**", "jwtFilter");
        return (AbstractShiroFilter) shiroFilter.getObject();
    }
}
