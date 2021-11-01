package stroom.ignite.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.ignite.api.ApacheIgniteService;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicLong;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ApacheIgniteServiceImpl implements ApacheIgniteService {

    //todo make these config properties
    private final long TIMEOUT_MS = 1000l;
    private final String CACHE_NAME = "StroomCache";

    private final String PERSISTENCE_PATH = "/tmp/ignite";
    private final Ignite ignite;

    @Inject
    ApacheIgniteServiceImpl (ClusterNodeManager nodeManager) {
        ClusterState clusterState = nodeManager.getClusterState();
        ignite = initIgnite(clusterState.getEnabledActiveNodes().stream().collect(Collectors.toList()));
    }

    @Override
    public Long incrementAndGetCounter(final long amount, final String keyType, final String key,
                                        final Instant timestamp, final Duration resolution) {

        final String counterName = keyType + key + resolution.toString();


        IgniteAtomicLong atomicLong = ignite.atomicLong(counterName, // Atomic long name.
                0, // Initial value.
                true // Create if it does not exist.
        );

        final long newValue = atomicLong.addAndGet(amount);

        return newValue;
    }

    private Ignite initIgnite(List<String> clusterMembers) {
        // Preparing IgniteConfiguration using Java APIs
        IgniteConfiguration igniteConfiguration = new IgniteConfiguration();

        //Logging comes first!
        igniteConfiguration.setGridLogger(new Slf4jLogger());

        //setting a work directory
        igniteConfiguration.setWorkDirectory(PERSISTENCE_PATH);

        //defining a partitioned cache, and basic properties for ignite
        CacheConfiguration cacheCfg = new CacheConfiguration(CACHE_NAME);
        cacheCfg.setCacheMode(CacheMode.PARTITIONED);
        cacheCfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);

        igniteConfiguration.setCacheConfiguration(cacheCfg);


        igniteConfiguration.setNetworkTimeout(TIMEOUT_MS);

        igniteConfiguration.setClientMode(false);

        // Classes of custom Java logic will be transferred over the wire from this app.
        igniteConfiguration.setPeerClassLoadingEnabled(true);

        // Setting up an IP Finder to ensure the client can locate the servers.
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
//        ipFinder.setAddresses(clusterMembers);
        ipFinder.setAddresses(Collections.singletonList("27.0.0.1:47500..47509"));
        igniteConfiguration.setDiscoverySpi(new TcpDiscoverySpi().setIpFinder(ipFinder));

        //data storage configuration
        DataStorageConfiguration storageCfg = new DataStorageConfiguration();
        storageCfg.getDefaultDataRegionConfiguration().setPersistenceEnabled(true);


        igniteConfiguration.setDataStorageConfiguration(storageCfg);

        // Starting the node
        return Ignition.start(igniteConfiguration);
    }

}
