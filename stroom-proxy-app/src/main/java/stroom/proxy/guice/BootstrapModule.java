package stroom.proxy.guice;

import com.google.inject.AbstractModule;
import stroom.util.spring.StroomBeanStore;

public class BootstrapModule extends AbstractModule {
    @Override
    protected void configure() {
//        bind(StroomBeanStore.class)

    }


//	<context:annotation-config />
//
//	<import resource="classpath:META-INF/spring/stroomRemoteServerContext.xml" />
//
//	<bean class="stroom.util.spring.StroomBeanStore" />
//	<bean class="stroom.util.spring.StroomBeanLifeCycle" />
//	<bean class="stroom.util.spring.StroomBeanLifeCycleReloadableContextBeanProcessor">
//		<property name="name" value="webContext" />
//	</bean>
//
//	<bean id="metaMapFactory" class="stroom.proxy.handler.MetaMapFactory" />
//	<bean id="metaMap" scope="request" factory-bean="metaMapFactory" factory-method="create" />
//	<bean id="dataFeedRequestHandler" class="stroom.proxy.datafeed.DataFeedRequestHandler" scope="request" />
//	<bean id="proxyRequestThreadLocalBuffer" class=" stroom.util.thread.ThreadLocalBuffer" />
//	<bean id="proxyHandlerFactory" class="stroom.proxy.datafeed.ProxyHandlerFactory" />
//	<bean id="remoteFeedService" class="stroom.proxy.feed.server.RemoteFeedServiceImpl" />
//	<bean id="remoteStatusService" class="stroom.proxy.status.server.RemoteStatusServiceImpl" />
//
//
//	<bean id="urlMapping" class="org.springframework.web.servlet.handler.SimpleUrlHandlerMapping">
//		<property name="lazyInitHandlers" value="true" />
//		<property name="mappings">
//			<props>
//				<prop key="/datafeed">dataFeedRequestHandler</prop>
//				<prop key="/datafeed/*">dataFeedRequestHandler</prop>
//				<prop key="/remoting/remotefeedservice.rpc">remoteFeedServiceRPC</prop>
//				<prop key="/remoting/remotestatusservice.rpc">remoteStatusServiceRPC</prop>
//			</props>
//		</property>
//	</bean>
//</beans>
}
