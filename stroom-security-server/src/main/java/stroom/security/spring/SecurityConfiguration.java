/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.spring;

import org.apache.commons.lang.StringUtils;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Scope;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import stroom.security.server.DBRealm;
import stroom.security.server.JWTAuthenticationFilter;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

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
@ComponentScan(
        basePackages = {"stroom.security.server", "stroom.security.shared"},
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class)
        }
)
public class SecurityConfiguration {
    public static final String PROD_SECURITY = "PROD_SECURITY";
    public static final String MOCK_SECURITY = "MOCK_SECURITY";
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfiguration.class);

    public SecurityConfiguration(){
        LOGGER.info("SecurityConfiguration loading...");
    }

    @Resource
    private DBRealm dbRealm;

    @Bean(name = "shiroFilter")
    public AbstractShiroFilter shiroFilter() throws Exception {
        final ShiroFilterFactoryBean shiroFilter = new ShiroFilterFactoryBean();
        shiroFilter.setSecurityManager(securityManager());
        shiroFilter.setLoginUrl("/login.html");
        shiroFilter.setSuccessUrl("/stroom.jsp");
        shiroFilter.getFilters().put("jwtFilter", new JWTAuthenticationFilter());
        shiroFilter.getFilterChainDefinitionMap().put("/**/secure/**", "authc, roles[USER]");
        shiroFilter.getFilterChainDefinitionMap().put("/api/auth/login", "anon");
        shiroFilter.getFilterChainDefinitionMap().put("/api/**", "jwtFilter");
        return (AbstractShiroFilter) shiroFilter.getObject();
    }

    @Bean(name = "securityManager")
    public org.apache.shiro.mgt.SecurityManager securityManager() {
        final DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(dbRealm);
        return securityManager;
    }

    @Bean(name = "mailSender")
    @Scope(StroomScope.PROTOTYPE)
    public MailSender mailSender() {
        final String host = StroomProperties.getProperty("stroom.mail.host");
        final int port = StroomProperties.getIntProperty("stroom.mail.port", 587);
        final String protocol = StroomProperties.getProperty("stroom.mail.protocol", "smtp");
        final String userName = StroomProperties.getProperty("stroom.mail.userName");
        final String password = StroomProperties.getProperty("stroom.mail.password");

        String propertiesFile = StroomProperties.getProperty("stroom.mail.propertiesFile");

        final JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(host);
        javaMailSender.setPort(port);
        javaMailSender.setProtocol(protocol);

        if (!StringUtils.isEmpty(userName)) {
            javaMailSender.setUsername(userName);
        }
        if (!StringUtils.isEmpty(password)) {
            javaMailSender.setPassword(password);
        }

        if (!StringUtils.isEmpty(propertiesFile)) {
            propertiesFile = propertiesFile.replaceAll("~", System.getProperty("user.home"));

            final File file = new File(propertiesFile);
            if (file.isFile()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    final Properties properties = new Properties();
                    properties.load(fis);
                    javaMailSender.setJavaMailProperties(properties);
                } catch (final IOException e) {
                    LOGGER.warn("Unable to load mail properties '" + propertiesFile + "'");
                }
            } else {
                LOGGER.warn("Mail properties not found at '" + propertiesFile + "'");
            }
        }

        return javaMailSender;
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public SimpleMailMessage resetPasswordTemplate() {
        final SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setSubject("Stroom - Password Reset");
        simpleMailMessage.setText("Dear ${username},\n\nYour Stroom password for host '${hostname}' has been reset.\n\nYour new password is '${password}'.\n\nThank you.");
        return simpleMailMessage;
    }
}
