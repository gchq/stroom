package stroom.proxy.guice;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import stroom.proxy.handler.LogRequestConfig;
import stroom.proxy.handler.ProxyRepositoryRequestHandlerProvider;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryReader;
import stroom.proxy.util.ProxyProperties;

public class ProxyStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        Names.bindProperties(binder(), ProxyProperties.instance());

        bind(LogRequestConfig.class);

        bind(ProxyRepositoryManager.class).asEagerSingleton();
        bind(ProxyRepositoryReader.class).asEagerSingleton();
        bind(ProxyRepositoryRequestHandlerProvider.class);

    }

//    @Provides
//    ProxyRepositoryRequestHandler provideProxyRepositoryRequestHandler() {
//        final ProxyRepositoryRequestHandler requestHandler = new ProxyRepositoryRequestHandler();
//    }
//
//
//    <bean name="proxyRepositoryRequestHandler" class="stroom.proxy.handler.ProxyRepositoryRequestHandler"
//    scope="thread">
//    </bean>
//
//    <bean name="proxyRepositoryReader" class="stroom.proxy.repo.ProxyRepositoryReader">
//        <property name="simpleCron" value="${readCron}"/>
//        <lookup-method name="createOutgoingRequestHandlerList"
//    bean="outgoingRequestHandlerList"/>
//    </bean>
//
//
//    <bean name="proxyRepositoryManager" class="stroom.proxy.repo.ProxyRepositoryManager">
//        <property name="repoDir" value="${repoDir}"/>
//        <property name="repositoryFormat" value="${repositoryFormat}"/>
//        <property name="simpleCron" value="${rollCron}"/>
//    </bean>
//
//    <bean class="stroom.util.spring.ListMerge">
//        <property name="target" ref="incomingRequestHandlerNameList"/>
//        <property name="source">
//            <list>
//                <value>proxyRepositoryRequestHandler</value>
//            </list>
//        </property>
//    </bean>


}
