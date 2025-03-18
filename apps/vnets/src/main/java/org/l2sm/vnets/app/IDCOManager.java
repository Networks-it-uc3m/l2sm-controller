package org.l2sm.vnets.app;

import static org.onlab.util.Tools.groupedThreads;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
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
// import org.onosproject.store.service.ConsistentMap;
// import org.onosproject.store.service.StorageService;

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

    // @Reference(cardinality = ReferenceCardinality.MANDATORY)
    // protected StorageService storageService;

    // private ConsistentMap<String, Integer> networkCuyito;

    private IDCODatabase database;
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

        // networkCuyito = storageService.<String, Integer>consistentMapBuilder()
        //         .withName("test-vnet-distributed")
        //         .withApplicationId(appId)
        //         .withPurgeOnUninstall()
        //         .build();

        this.database = new IDCODatabase(log);

        packetProcessor = new ArpProxyPacketProcessor();
        packetService.addProcessor(packetProcessor, PacketProcessor.director(2));

        vnfLocationProvider = new VNFLocationProvider();
        packetService.addProcessor(vnfLocationProvider, PacketProcessor.advisor(1));

        intentListener = new CustomIntentListener();
        intentService.addListener(intentListener);

        genericEventHandler = Executors.newFixedThreadPool(4, groupedThreads("idco/event-handler", "worker-%d", log));

        vnfLocationProvider.requestIntercepts();

        tunnelIdProvider = new TunnelIdProvider();

        log.info("IDCO was started");

    }

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
        database.getAllIntents().forEach(intentKey -> {
            Intent intent = intentService.getIntent(intentKey);
            if (intent != null) {
                intentService.withdraw(intent);
            }
        });

        log.info("Clearing database");
        database.cleanDatabases();
        intentService.removeListener(intentListener);

        log.info("IDCO has stopped");
    }

    public void createNetworkCuyito(String networkId) {
        networkStorage.putIfAbsent(networkId,new Network(networkId));
    }

    public void createVirtualNetwork(String networkId) throws IDCOServiceException {
      
        genericEventHandler.submit(() -> {
            log.info("Creating network: " + networkId);
            try {
                if (database.networkExists(networkId)) {
                    throw new IDCOServiceException("The network already exists");
                }
                log.info("Registering new network");
                database.registerNetwork(networkId);
                log.info("The network " + networkId + " was correctly created");
            } catch (IDCOServiceException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
        });
    }

    public void deleteNetworkCuyito(String networkId) {
        // networkCuyito.remove(networkId);

    }

    public void deleteVirtualNetwork(String networkId) throws IDCOServiceException {

        genericEventHandler.submit(() -> {
            log.info("Deleting network " + networkId);
            try {
                Collection<Key> netIntent = database.getNetworkIntents(networkId);
                if (netIntent == null) {
                    throw new IDCOServiceException("The network does not exist");
                }
                log.info("Deleting intents for network " + networkId);
                netIntent.forEach(intentKey -> {
                    Intent intent = intentService.getIntent(intentKey);
                    if (intent != null) {
                        intentService.withdraw(intent);
                    }
                });
                log.info("Deleting network " + networkId + " from the database");
                database.deleteNetwork(networkId);
                log.info("The network with id \"" + networkId + "\" has been deleted");
            } catch (IDCOServiceException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
        });
    }

    public void addPort(String networkId, ConnectPoint networkEndpoint) throws IDCOServiceException {
        genericEventHandler.submit(() -> {
            log.info("Adding port " + networkEndpoint.toString() + " to network " + networkId);
            try {
                if (!database.networkExists(networkId)) {
                    throw new IDCOServiceException("The network does not exist");
                }

                Long tunnelId = tunnelIdProvider.getNewId();
                log.info("Adding port " + networkEndpoint + " to network " + networkId + " to the database");
                database.addPortToNetwork(networkId, networkEndpoint, tunnelId);
                log.info("Port " + networkEndpoint + " in network " + networkId + " added to the database");

                Network network = database.getNetwork(networkId);
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
                    intentService.submit(intent);
                    log.info("Adding main intent to database for the network " + networkId);
                    database.addMainIntent(networkId, intentKey);
                }
                log.info("Port " + networkEndpoint + " correctly added to " + networkId);
            } catch (IDCOServiceException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
    }

    public Network getVirtualNetwork(String networkId) throws IDCOServiceException {
        log.info("Manager tries to retrieve network " + networkId);

        Future<Network> future = genericEventHandler.submit(() -> {
            log.info("Retrieving network " + networkId);
            return database.getNetwork(networkId);
            
        });

        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            log.error("Network doesn't exist");
            return null;
        } catch (Exception e){
            log.error("Error retrieving network", e);
            return null;
        }
    }
    public Integer getNetworkCuyito(String networkId) {
        
        return new Integer(7);
        // return networkCuyito.get(networkId).value();
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

            String mscsId = database.getNetworkIdForPort(heardPort);
            if (mscsId == null) {
                return;
            }

                if (!(dstMac.isBroadcast() || dstMac.isMulticast())) {
                    ConnectPoint hostLocation = database.getHostLocation(mscsId, dstMac);
                    if (hostLocation != null) {
                        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                .setOutput(hostLocation.port())
                                .build();
                        OutboundPacket outboundPacket = new DefaultOutboundPacket(hostLocation.deviceId(), treatment,
                                context.inPacket().unparsed());
                        packetService.emit(outboundPacket);
                        context.block();
                        return;
                    }
                }

                Collection<ConnectPoint> connectPoints = database.getPortsOfNetworkGivenPort(heardPort);
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
            String mscsId = database.getNetworkIdForPort(hostLocation);
            if (mscsId == null) {
                return;
            }
                log.info("New packet received: " + macAddress.toString() + " for network " + mscsId);

                ConnectPoint lastLocation = database.getHostLocation(mscsId, macAddress);
                if (lastLocation != null) {
                    if (!lastLocation.equals(hostLocation)) {
                        log.warn("The host " + macAddress + " in network " + mscsId
                                + " has changed its location. The system does not support host mobility");
                    }

                    return;
                }

                Long tunnelId = database.getTunnelIdOfPort(hostLocation);
                if (tunnelId == null) {
                    context.block();
                    return;
                }

                Collection<ConnectPoint> connectPoint = database.getPortsOfNetworkGivenPort(hostLocation);

                List<FlowRule> rules = connectPoint.stream()
                        .map(point -> createRule(macAddress, hostLocation, point, tunnelId))
                        .collect(Collectors.toList());

                Key key = generateHostIntentKey(macAddress, mscsId);

                FlowRuleIntent ruleIntent = new FlowRuleIntent(appId, key, rules,
                        Collections.emptyList(), PathIntent.ProtectionType.PRIMARY, null);

                intentService.submit(ruleIntent);
                database.addIntentToNetwork(mscsId, key);
                database.setHostLocation(mscsId, macAddress, hostLocation);
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
}
