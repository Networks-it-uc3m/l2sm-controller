package org.l2sm.vnets.app;

import static org.onlab.util.Tools.groupedThreads;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vnets.api.IDCOService;
import org.l2sm.vnets.api.IDCOServiceException;
import org.l2sm.vnets.api.Network;
import org.l2sm.vnets.net.VirtualLinkIntent;
import org.l2sm.vnets.net.VirtualNetworkIntent;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.intentsync.IntentSynchronizationService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.intent.FlowRuleIntent;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentEvent;
import org.onosproject.net.intent.IntentListener;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.ObjectiveTrackerService;
import org.onosproject.net.intent.PathIntent;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.Versioned;
import org.onosproject.store.service.Serializer;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Longs;

@Component(immediate = true)
@Service
public class IDCOManager implements IDCOService {

    private static final int VIRTUAL_LINK_PRIORITY = PacketPriority.HIGH3.priorityValue();
    private static final PacketPriority ARP_TO_CONTROLLER_PRIORITY = PacketPriority.HIGH2;
    private static final int VIRTUAL_NETWORK_CORE_PRIORITY = PacketPriority.HIGH1.priorityValue();
    private static final int VIRTUAL_NETWORK_EDGE_PRIORITY = PacketPriority.HIGH1.priorityValue();

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigService networkConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ObjectiveTrackerService objectiveTrackerService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;
    
    // @Reference(cardinality = ReferenceCardinality.MANDATORY)
    // protected IntentSynchronizationService intentSynchronizer;

    // @Reference(cardinality = ReferenceCardinality.MANDATORY)
    // protected IntentService intentService;

    private ConsistentMap<String, Network> networkStorage;
    
    private ConsistentMap<ConnectPoint, Port> connectionPointStorage;
    private ConsistentMap<Key, Intent> intentStorage;
    private ConsistentMap<MacCompositeKey, ConnectPoint> macStorage;

    private static class Port {
        private String networkId;
        private Long tunnelId;
      
        public Port(String networkId, Long tunnelId) {
            this.networkId = networkId;
            this.tunnelId = tunnelId;
        }

        public String getNetworkId() {
            return networkId;
        }
        
        public Long getTunnelId() {
            return tunnelId;
        }
       
        
    }

    protected static class MacCompositeKey {
        private final String networkId;
        private final MacAddress mac;

        public MacCompositeKey(String networkId, MacAddress mac) {
            this.networkId = networkId;
            this.mac = mac;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((networkId == null) ? 0 : networkId.hashCode());
            result = prime * result + ((mac == null) ? 0 : mac.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MacCompositeKey other = (MacCompositeKey) obj;
            if (networkId == null) {
                if (other.networkId != null)
                    return false;
            } else if (!networkId.equals(other.networkId))
                return false;
            if (mac == null) {
                if (other.mac != null)
                    return false;
            } else if (!mac.equals(other.mac))
                return false;
            return true;
        }
    
        
        
    }

    private TunnelIdProvider tunnelIdProvider;
 
    private ArpProxyPacketProcessor packetProcessor;
    private VNFLocationProvider vnfLocationProvider;
    private CustomIntentListener intentListener;

    private ExecutorService genericEventHandler;
    
    private ApplicationId appId;

    @Activate
    protected void activate() {
        log.info("Starting IDCO");
        appId = coreService.registerApplication("org.l2sm.vnets.app");
        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
        .register(KryoNamespaces.API)
        .register(Network.class)
        .register(VirtualNetworkIntent.class)
        .register(VirtualLinkIntent.class)
        .register(ConnectPoint.class)
        .register(MacCompositeKey.class)
        .register(Port.class);
    
        networkStorage = storageService.<String, Network>consistentMapBuilder()
            .withName("network-storage")
            .withApplicationId(appId)
            .withSerializer(Serializer.using(serializer.build()))
            .withPurgeOnUninstall()
            .build();


        connectionPointStorage = storageService.<ConnectPoint, Port>consistentMapBuilder()
            .withName("connection-point-storage")
            .withApplicationId(appId)
            .withSerializer(Serializer.using(serializer.build()))
            .withPurgeOnUninstall()
            .build();

        macStorage = storageService.<MacCompositeKey, ConnectPoint>consistentMapBuilder()
            .withName("mac-storage")
            .withApplicationId(appId)
            .withSerializer(Serializer.using(serializer.build()))
            .withPurgeOnUninstall()
            .build();


        intentStorage = storageService.<Key, Intent>consistentMapBuilder()
            .withName("intent-storage")            
            .withApplicationId(appId)            
            .withSerializer(Serializer.using(serializer.build()))            
            .withPurgeOnUninstall()            
            .build();

        packetProcessor = new ArpProxyPacketProcessor();
        packetService.addProcessor(packetProcessor, PacketProcessor.director(2));

        vnfLocationProvider = new VNFLocationProvider();
        packetService.addProcessor(vnfLocationProvider, PacketProcessor.advisor(1));

        // intentListener = new CustomIntentListener();
        // intentService.addListener(intentListener);

        genericEventHandler = Executors.newFixedThreadPool(4, groupedThreads("idco/event-handler", "worker-%d", log));

        vnfLocationProvider.requestIntercepts();

        tunnelIdProvider = new TunnelIdProvider();

        log.info("IDCO was started");


    }
    // private void statusChange(DistributedPrimitive.Status status) {
    //     switch (status) {
    //     case ACTIVE:
    //         startProcessing();
    //         break;
    //     case SUSPENDED:
    //         stopProcessing();
    //         break;
    //     case INACTIVE:
    //     default:
    //         break;
    //     }
    // }
    // private void startProcessing() {
    //     intentsExecutor = createExecutor();

    //     intentQueue.registerTaskProcessor(this::createIntent, NUM_PARALLEL_JOBS, intentsExecutor);
    // }
    // protected ExecutorService createExecutor() {
    //     return newSingleThreadExecutor(groupedThreads("onos/" + appId, "sync", log));
    // }


    // private void stopProcessing() {
    //     intentQueue.stopProcessing();
    // }

    // private void createIntent(Intent intent) {
    //     log.info("Creating intent {}", intent);
    //     intent.
    //     if (node.equals(clusterService.getLocalNode().id())) {
    //         log.debug("Do not remove routes from local nodes {}", node);
    //         return;
    //     }

    //     if (clusterService.getState(node) == ControllerNode.State.READY) {
    //         log.debug("Do not remove routes from active nodes {}", node);
    //         return;
    //     }

    //     log.debug("Withdrawing routes: {}", routes);
    //     routeService.withdraw(routes);
    // }
    @Deactivate
    protected void deactivate() {
        log.info("Starting the IDCO cleaning process");

        log.info("Removing interceptors and packet processors");
        vnfLocationProvider.withdrawIntercepts();
        packetService.removeProcessor(packetProcessor);
        packetService.removeProcessor(vnfLocationProvider);

        log.info("Shutting down the event handler");
        try {
            genericEventHandler.shutdown();
            if (!genericEventHandler.awaitTermination(60, TimeUnit.SECONDS)) {
                genericEventHandler.shutdownNow();
                if (!genericEventHandler.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            genericEventHandler.shutdownNow();
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }

        log.info("Withdrawing all the intents");

        // networkStorage.stream().forEach(networkCons -> {
        //     Network network = networkCons.getValue().value();
        //     network.getIntents().forEach(intentKey -> {
        //         Intent intent = intentSynchronizer.getIntent(intentKey);
        //         if (intent != null) {
        //             intentSynchronizer.withdraw(intent);
        //         }
        //     });
        // });
        intentService.getIntents().forEach(i -> {
                log.info(i.toString());
                if(i.appId() == appId) {
                    intentService.withdraw(i);
                    intentService.purge(i);
                }
            });
        // intentSynchronizer.removeIntentsByAppId(appId);
        // intentSynchronizer.getIntents().forEach(i -> {
        //     log.info(i.toString());
        //     if(i.appId() == appId) {
        //         intentSynchronizer.withdraw(i);
        //         intentSynchronizer.purge(i);
        //     }
        // });
        

        log.info("Clearing database");
        networkStorage.clear();
        macStorage.clear();
        connectionPointStorage.clear();

        // intentSynchronizer.removeListener(intentListener);

        log.info("IDCO has stopped");
    }

    public void createVirtualNetwork(String networkId) {
        networkStorage.putIfAbsent(networkId,new Network(networkId));
    }

   

    public void deleteVirtualNetwork(String networkId) {
    
        if(!networkStorage.containsKey(networkId)) {
            log.info("Network "+ networkId + " doesn't exist");
            return;
        }
        Network network = networkStorage.get(networkId).value();

        if(network.getIntents().isEmpty()) {
            log.info("Network "+ networkId + " doesn't have any intents to delete");
        } else {
            log.info("Deleting intents for network " + networkId);

           network.getIntents().forEach(intentKey -> {
                log.debug("intent key: ", intentKey);
                Versioned<Intent> intent =  intentStorage.get(intentKey);

                log.debug("erasing intent: ", intent);
        
                if (intent != null) {
                    intentService.withdraw(intent.value());
                    // intentSynchronizer.withdraw(intent.value());
                }
            }); 
        }
        if(!network.getNetworkEndpoints().isEmpty()) {
            network.getNetworkEndpoints().forEach(networkEndpoint -> {
                connectionPointStorage.remove(networkEndpoint);
            });
        }
        log.info("Deleting network " + networkId + " from the storage");
        Set<MacCompositeKey> macKeysToRemove = macStorage.keySet()
            .stream()
            .filter(k -> k.networkId.equals(networkId))
            .collect(Collectors.toSet());

        // Now remove each one from macStorage
        macKeysToRemove.forEach(k -> macStorage.remove(k));
        networkStorage.remove(networkId);
        log.info("The network with id \"" + networkId + "\" has been deleted");
       
    }



    public void addPort(String networkId, ConnectPoint networkEndpoint) throws IDCOServiceException {
        log.info("Adding port " + networkEndpoint.toString() + " to network " + networkId);
        if (!networkStorage.containsKey(networkId)) {
            throw new IDCOServiceException("The network does not exist");
        }

        Long tunnelId = tunnelIdProvider.getNewId();
        log.info("Adding port " + networkEndpoint + " to network " + networkId + " to the database");
        Network network = networkStorage.compute(networkId, (key,oldNetwork) ->{
            oldNetwork.networkEndpoints.add(networkEndpoint);
            oldNetwork.tunnelIds.add(tunnelId);
            return oldNetwork;
        }).value();
        connectionPointStorage.put(networkEndpoint, new Port(networkId,tunnelId));

        log.info("Port " + networkEndpoint + " in network " + networkId + " added to the database");

        int size = network.getNetworkEndpoints().size();

        ConnectPoint[] netCps = new ConnectPoint[size];
        network.getNetworkEndpoints().toArray(netCps);
        long[] ids = Longs.toArray(network.getIds());

        Intent intent = null;
        Key intentKey = Key.of("idco-main-" + networkId, appId);
        log.info("Creating main intent for network " + networkId);
        if (size == 1) {
            log.info("Network has only one port, no intent is created");
        } else if (size == 2) {
            log.info("Creating virtual link intent between points " + netCps[0] + " and " + netCps[1]);
            intent = VirtualLinkIntent.builder()
                    .key(intentKey)
                    .appId(appId)
                    .one(netCps[0])
                    .two(netCps[1])
                    .priority(VIRTUAL_LINK_PRIORITY)
                    .tunnelID(ids[0])
                    .build();
        } else {
            log.info("Creating virtual network intent");
            intent = VirtualNetworkIntent.builder()
                    .key(intentKey)
                    .appId(appId)
                    .connectPoints(netCps)
                    .priority(VIRTUAL_NETWORK_CORE_PRIORITY)
                    .tunnelIDs(ids)
                    .build();
        }
        if (intent != null) {
            log.info("Submitting new main intent for network " + networkId);
            intentStorage.put(intentKey, intent);
            // intentSynchronizer.submit(intent);
            intentService.submit(intent);
            log.info("Adding main intent to database for the network " + networkId);
            networkStorage.compute(networkId, (key,oldNetwork) ->{
                oldNetwork.getIntents().add(intentKey);
                return oldNetwork;
            });            
        }
        log.info("Port " + networkEndpoint + " correctly added to " + networkId);
          
    }

    public Network getVirtualNetwork(String networkId) {
        if(!networkStorage.containsKey(networkId)) {
            log.info("Network " + networkId + " doesn't exist.");
            return null;
        }
        Network network = networkStorage.get(networkId).value();
        log.info(network.toString());
        log.info(network.getIntents().toString());
        return networkStorage.get(networkId).value();
    }

    class ArpProxyPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Verify valid context
            if (context == null || context.isHandled()) {
                return;
            }
            // Verify valid Ethernet packet
            Ethernet eth = context.inPacket().parsed();
            if (eth == null) {
                return;
            }

            genericEventHandler.submit(() -> processPacketInternal(context));
        }

        public void processPacketInternal(PacketContext context) {
            Ethernet eth = context.inPacket().parsed();
            MacAddress dstMac = eth.getDestinationMAC();
            ConnectPoint heardPort = context.inPacket().receivedFrom();

            if (!connectionPointStorage.containsKey(heardPort)) {
                return;
            }
            String mscsId = connectionPointStorage.get(heardPort).value().getNetworkId();
           
            MacCompositeKey macKey = new MacCompositeKey(mscsId,dstMac);
            if (!(dstMac.isBroadcast() || dstMac.isMulticast()) && macStorage.containsKey(macKey)) {
                ConnectPoint hostLocation = macStorage.get(macKey).value();
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setOutput(hostLocation.port())
                        .build();
                OutboundPacket outboundPacket = new DefaultOutboundPacket(hostLocation.deviceId(), treatment,
                        context.inPacket().unparsed());
                packetService.emit(outboundPacket);
                context.block();
                return;
            }

            Collection<ConnectPoint> connectPoints = Collections.emptySet();
            if(networkStorage.containsKey(mscsId)) {
                connectPoints = networkStorage.get(mscsId).value().getNetworkEndpoints().stream().filter(p -> !p.equals(heardPort)).collect(Collectors.toSet());
            }
            connectPoints.forEach(point -> {
                TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(point.port()).build();
                OutboundPacket outboundPacket = new DefaultOutboundPacket(point.deviceId(), treatment,
                        context.inPacket().unparsed());
                packetService.emit(outboundPacket);
            });

            context.block();
            log.info("Proxying packet for: " + dstMac.toString() + " in network " + mscsId);
        }
    }

    private class VNFLocationProvider implements PacketProcessor {

        /**
         * Request packet intercepts.
         */
        private void requestIntercepts() {
            // Use ARP
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_ARP);
            packetService.requestPackets(selector.build(), ARP_TO_CONTROLLER_PRIORITY, appId);
        }

        /**
         * Withdraw packet intercepts.
         */
        private void withdrawIntercepts() {
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_ARP);
            packetService.cancelPackets(selector.build(), ARP_TO_CONTROLLER_PRIORITY, appId);
        }

        @Override
        public void process(PacketContext context) {
            // Verify valid context
            if (context == null) {
                return;
            }
            // Verify valid Ethernet packet
            Ethernet eth = context.inPacket().parsed();
            if (eth == null) {
                return;
            }
            MacAddress srcMac = eth.getSourceMAC();
            if (srcMac.isBroadcast() || srcMac.isMulticast()) {
                return;
            }

            genericEventHandler.submit(() -> processPacketInternal(context), Objects.hash(srcMac));
        }

        private void processPacketInternal(PacketContext context) {
            Ethernet eth = context.inPacket().parsed();
            ConnectPoint heardOn = context.inPacket().receivedFrom();

            // If this arrived on control port, bail out.
            if (heardOn.port().isLogical()) {
                return;
            }

            MacAddress hostId = eth.getSourceMAC();
            if (eth.getEtherType() == Ethernet.TYPE_ARP) {
                detectedHost(hostId, heardOn, context);
            }
        }

        public void detectedHost(MacAddress macAddress, ConnectPoint hostLocation, PacketContext context) {
            if(!connectionPointStorage.containsKey(hostLocation)) {
                return;
            }
            String mscsId = connectionPointStorage.get(hostLocation).value().getNetworkId();

            log.info("New packet received: " + macAddress.toString() + " for network " + mscsId);

            MacCompositeKey macKey = new MacCompositeKey(mscsId, macAddress);

            if (macStorage.containsKey(macKey)) {
                ConnectPoint lastLocation = macStorage.get(macKey).value();
                if (!lastLocation.equals(hostLocation)) {
                    log.warn("The host " + macAddress + " in network " + mscsId
                            + " has changed its location. The system does not support host mobility");
                }
                return;
            }
            
            if(!connectionPointStorage.containsKey(hostLocation)) {
                context.block();
                return; 
            } 
            Long tunnelId = connectionPointStorage.get(hostLocation).value().getTunnelId();

            if (tunnelId == null) {
                context.block();
                return;
            }

            Collection<ConnectPoint> connectPoints = Collections.emptySet();
            if(networkStorage.containsKey(mscsId)) {
                connectPoints = networkStorage.get(mscsId).value().getNetworkEndpoints().stream().filter(p -> !p.equals(hostLocation)).collect(Collectors.toSet());
            }

            List<FlowRule> rules = connectPoints.stream()
                    .map(point -> createRule(macAddress, hostLocation, point, tunnelId))
                    .collect(Collectors.toList());

            Key intentKey = generateHostIntentKey(macAddress, mscsId);

            FlowRuleIntent ruleIntent = new FlowRuleIntent(appId, intentKey, rules,
                    Collections.emptyList(), PathIntent.ProtectionType.PRIMARY, null);
            
            intentStorage.put(intentKey, ruleIntent);
            // intentSynchronizer.submit(ruleIntent);
            intentService.submit(ruleIntent);
            networkStorage.compute(mscsId, (key,oldNetwork) ->{
                oldNetwork.getIntents().add(intentKey);
                return oldNetwork;
            });
            macStorage.put(macKey, hostLocation);
        }

        private FlowRule createRule(MacAddress address, ConnectPoint cp, ConnectPoint otherCp, long tunnelId) {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setTunnelId(tunnelId).transition(1).build();
            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthDst(address).matchInPort(otherCp.port()).build();
            return DefaultFlowRule.builder().fromApp(appId)
                    .withPriority(VIRTUAL_NETWORK_EDGE_PRIORITY)
                    .withTreatment(treatment)
                    .withSelector(selector)
                    .makePermanent()
                    .forDevice(otherCp.deviceId())
                    .build();
        }
    }

    class CustomIntentListener implements IntentListener {

        @Override
        public void event(IntentEvent event) {
            genericEventHandler.submit(() -> handleEvent(event));
        }

        public void handleEvent(IntentEvent event) {
            Intent intent = event.subject();
            log.info("Intent event: " + event.type().name());
            switch (event.type()) {
                case FAILED:
                    // objectiveTrackerService.addTrackedResources(intent.key(), );
                    break;
                case INSTALLED:
                    break;
                case WITHDRAWN:
                    intentService.purge(intent);
                    break;
                default:
                    break;
            }
        }
    }

    /************ UTILS ***************************************/

    private Key generateHostIntentKey(MacAddress hostMac, String mscsId) {
        return Key.of("idco-host-" + mscsId + "-" + hostMac, appId);
    }

    /*
     * We are not focusing on security in this implementation. Future implementations
     * will include a more secure Tunnel Id provider.
     * TODO: check valid vxlan tunnels
     */
    static class TunnelIdProvider {

        /*
         * Linear Congruent generator for 24 bit numbers
         * The parameters c and a are chosen to make the period 2*24:
         * - c is relatively prime to 2^24
         * - 2 is a factor of a - 1
         * - 4 is a factor of a - 1
         * 
         * TODO: generate this values dynamically
         */
        private long c = 16777213;
        private long a = 258088 + 1;
        private long modulus = 16777216;
        private long lastId;
        private long count;

        public TunnelIdProvider() {
            IdGenerator generator = new IdGenerator();
            this.lastId = generator.nextId();
            count = 0;
        }

        public Long getNewId() {
            long id;
            if (count == modulus) {
                return null;
            }
            id = lastId;
            this.lastId = (lastId * a + c) % modulus;
            count++;
            return id;
        }

        private class IdGenerator extends SecureRandom {
            public IdGenerator() {
                super();
            }

            public long nextId() {
                return next(24);
            }
        }
    }

    @Override
    public void deletePort(String networkId, ConnectPoint networkEndpoint) throws IDCOServiceException {

        log.info("Deleting port " + networkEndpoint.toString() + " from network " + networkId);
        if (!networkStorage.containsKey(networkId)) {
            throw new IDCOServiceException("The network does not exist");
        }

    
        Port port = connectionPointStorage.get(networkEndpoint).value();
        Long tunnelId = port.getTunnelId();
        log.info("Deleting port " + networkEndpoint + " from network " + networkId + " from the database");
        Network network = networkStorage.compute(networkId, (key,oldNetwork) ->{
            oldNetwork.networkEndpoints.remove(networkEndpoint);
            oldNetwork.tunnelIds.remove(tunnelId);
            return oldNetwork;
        }).value();
        connectionPointStorage.remove(networkEndpoint);

        log.info("Port " + networkEndpoint + " in network " + networkId + " removed from the database");

        addUpdatedIntent();
        log.info("Port " + networkEndpoint + " deleted from " + networkId);
          
    }
    public void addUpdatedIntent(Network network) {
        
        int size = network.getNetworkEndpoints().size();

        ConnectPoint[] netCps = new ConnectPoint[size];
        network.getNetworkEndpoints().toArray(netCps);
        long[] ids = Longs.toArray(network.getIds());

        Intent intent = null;
        Key intentKey = Key.of("idco-main-" + network.getNetworkId(), appId);
        log.info("Updating main intent for network " + network.getNetworkId());
        if (size == 1) {
            log.info("Network has only one port, no intent is created");
        } else if (size == 2) {
            log.info("Creating virtual link intent between points " + netCps[0] + " and " + netCps[1]);
            intent = VirtualLinkIntent.builder()
                    .key(intentKey)
                    .appId(appId)
                    .one(netCps[0])
                    .two(netCps[1])
                    .priority(VIRTUAL_LINK_PRIORITY)
                    .tunnelID(ids[0])
                    .build();
        } else {
            log.info("Creating virtual network intent");
            intent = VirtualNetworkIntent.builder()
                    .key(intentKey)
                    .appId(appId)
                    .connectPoints(netCps)
                    .priority(VIRTUAL_NETWORK_CORE_PRIORITY)
                    .tunnelIDs(ids)
                    .build();
        }
        if (intent != null) {
            log.info("Submitting new main intent for network " + networkId);
            intentStorage.put(intentKey, intent);
            // intentSynchronizer.submit(intent);
            intentService.submit(intent);
            log.info("Adding main intent to database for the network " + networkId);
            networkStorage.compute(networkId, (key,oldNetwork) ->{
                oldNetwork.getIntents().add(intentKey);
                return oldNetwork;
            });            
        }
    }