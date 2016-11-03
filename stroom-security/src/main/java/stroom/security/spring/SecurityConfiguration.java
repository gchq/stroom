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

package stroom.security.spring;

import stroom.security.server.DBRealm;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import javax.annotation.Resource;

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
@ComponentScan(basePackages = {"stroom.security.server", "stroom.security.shared"}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class SecurityConfiguration {
    public static final String PROD_SECURITY = "PROD_SECURITY";
    public static final String MOCK_SECURITY = "MOCK_SECURITY";

    @Resource
    private DBRealm dbRealm;

    @Bean(name = "shiroFilter")
    public AbstractShiroFilter shiroFilter() throws Exception {
        final ShiroFilterFactoryBean shiroFilter = new ShiroFilterFactoryBean();
        shiroFilter.setSecurityManager(securityManager());
        shiroFilter.setLoginUrl("/login.html");
        shiroFilter.setSuccessUrl("/stroom.jsp");
        shiroFilter.getFilterChainDefinitionMap().put("/**/secure/*", "authc, roles[USER]");
        return (AbstractShiroFilter) shiroFilter.getObject();
    }

    @Bean(name = "securityManager")
    public org.apache.shiro.mgt.SecurityManager securityManager() {
        final DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(dbRealm);
        return securityManager;
    }
}
