package stroom.proxy.app.guice;

import stroom.proxy.app.ContentSyncConfig;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyPathConfig;
import stroom.proxy.app.RestClientConfig;
import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ReceiptPolicyConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.proxy.repo.RepoConfig;
import stroom.util.io.PathConfig;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsProxyConfig;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProxyConfigModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfigModule.class);

    private final ProxyConfig proxyConfig;

    public ProxyConfigModule(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    protected void configure() {
        // Bind the application config.
        bind(ProxyConfig.class).toInstance(proxyConfig);

        // ProxyConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own.
        // This allows gradle modules to know nothing about the other modules.
        // Our bind method has the arguments in the reverse way to guice so we can
        // more easily see the tree structure. Each config pojo must extend AbstractConfig.
        // Some config objects are shared and thus should not be bound here and should be
        // annotated with NotInjectableConfig

        // WARNING If you don't bind the class here and then inject that config class then
        // you will get a vanilla instance that is not linked to the config yml or db props!
        // It is safer to bind everything to be on the safe side
        bindConfig(ProxyConfig::getAggregatorConfig, ProxyConfig::setAggregatorConfig, AggregatorConfig.class);
        bindConfig(ProxyConfig::getContentSyncConfig, ProxyConfig::setContentSyncConfig, ContentSyncConfig.class);
        bindConfig(ProxyConfig::getFeedStatusConfig, ProxyConfig::setFeedStatusConfig, FeedStatusConfig.class);
        bindConfig(ProxyConfig::getForwarderConfig, ProxyConfig::setForwarderConfig, ForwarderConfig.class);
        bindConfig(ProxyConfig::getLogStreamConfig, ProxyConfig::setLogStreamConfig, LogStreamConfig.class);
        // Bind it to PathConfig and ProxyPathConfig so classes that inject PathConfig work
        bindConfig(ProxyConfig::getProxyPathConfig,
                ProxyConfig::setProxyPathConfig,
                ProxyPathConfig.class,
                PathConfig.class);
        bindConfig(ProxyConfig::getProxyRepositoryConfig,
                ProxyConfig::setProxyRepositoryConfig,
                ProxyRepoConfig.class,
                RepoConfig.class);
        bindConfig(ProxyConfig::getProxyRepoFileScannerConfig,
                ProxyConfig::setProxyRepoFileScannerConfig,
                ProxyRepoFileScannerConfig.class);
        bindConfig(ProxyConfig::getReceiptPolicyConfig, ProxyConfig::setReceiptPolicyConfig, ReceiptPolicyConfig.class);
        bindConfig(ProxyConfig::getRestClientConfig, ProxyConfig::setRestClientConfig, RestClientConfig.class);
    }

    private <T extends IsProxyConfig> void bindConfig(
            final Function<ProxyConfig, T> configGetter,
            final BiConsumer<ProxyConfig, T> configSetter,
            final Class<T> clazz) {
        bindConfig(proxyConfig, configGetter, configSetter, clazz, clazz, null);
    }

    private <T extends IsProxyConfig> void bindConfig(
            final Function<ProxyConfig, T> configGetter,
            final BiConsumer<ProxyConfig, T> configSetter,
            final Class<T> instanceClass,
            final Class<? super T> bindClass) {
        bindConfig(proxyConfig, configGetter, configSetter, instanceClass, bindClass, null);
    }

    private <T extends IsProxyConfig> void bindConfig(
            final Function<ProxyConfig, T> configGetter,
            final BiConsumer<ProxyConfig, T> configSetter,
            final Class<T> clazz,
            final Consumer<T> childConfigConsumer) {
        bindConfig(proxyConfig, configGetter, configSetter, clazz, clazz, childConfigConsumer);
    }

    private <X extends IsProxyConfig, T extends IsProxyConfig> void bindConfig(
            final X parentObject,
            final Function<X, T> configGetter,
            final BiConsumer<X, T> configSetter,
            final Class<T> clazz) {
        bindConfig(parentObject, configGetter, configSetter, clazz, clazz, null);
    }

    private <X extends IsProxyConfig, T extends IsProxyConfig> void bindConfig(
            final X parentObject,
            final Function<X, T> configGetter,
            final BiConsumer<X, T> configSetter,
            final Class<T> instanceClass,
            final Class<? super T> bindClass,
            final Consumer<T> childConfigConsumer) {

        if (parentObject == null) {
            throw new RuntimeException(LogUtil.message("Unable to bind config to {} as the parent is null. " +
                            "You may have an empty branch in your config YAML file.",
                    instanceClass.getCanonicalName()));
        }

        try {
            // Get the config instance
            T configInstance = configGetter.apply(parentObject);

            if (configInstance == null) {
                // branch with no children in the yaml so just create a default one
                try {
                    configInstance = instanceClass.getConstructor().newInstance();
                    // Now set the new instance on the parent
                    configSetter.accept(parentObject, configInstance);
                } catch (Exception e) {
                    throw new RuntimeException(LogUtil.message(
                            "Class {} does not have a no args constructor", instanceClass.getName()));
                }
            }

            LOGGER.debug("Binding instance of {} to class {}", instanceClass.getName(), bindClass.getName());
            bind(bindClass).toInstance(configInstance);
            if (!bindClass.equals(instanceClass)) {
                // bind class and instance class differ so bind to both, e.g.
                // an instance of ProxyPathConfig gets bound to PathConfig and ProxyPathConfig
                LOGGER.debug("Binding instance of {} to class {}", instanceClass.getName(), instanceClass.getName());
                bind(instanceClass).toInstance(configInstance);
            }
            if (childConfigConsumer != null) {
                childConfigConsumer.accept(configInstance);
            }
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error binding getter on object {} to class {}",
                    parentObject.getClass().getCanonicalName(),
                    bindClass.getCanonicalName()),
                    e);
        }
    }
}
