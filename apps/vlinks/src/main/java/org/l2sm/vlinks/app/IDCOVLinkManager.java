package org.l2sm.vlinks.app;

import static org.onlab.util.Tools.groupedThreads;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vlinks.api.IDCOVLinkService;
import org.l2sm.vlinks.api.IDCOVLinkServiceException;
import org.l2sm.vlinks.api.VLinkNetwork;
import org.l2sm.vlinks.net.VLinkPathIntent;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.util.KryoNamespace;
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
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
@Service
public class IDCOVLinkManager implements IDCOVLinkService {

    private static final int VIRTUAL_LINK_PRIORITY = PacketPriority.HIGH3.priorityValue();
    private static final PacketPriority ARP_TO_CONTROLLER_PRIORITY = PacketPriority.HIGH2;
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

    private ConsistentMap<String, VLinkNetwork> networkStorage;
    private ConsistentMap<ConnectPoint, Port> connectionPointStorage;
    private ConsistentMap<MacCompositeKey, ConnectPoint> macStorage;

    private TunnelIdProvider tunnelIdProvider;

    private ArpProxyPacketProcessor packetProcessor;
    private VNFLocationProvider vnfLocationProvider;
    private CustomIntentListener intentListener;

    private ExecutorService genericEventHandler;

    private ApplicationId appId;

    @Activate
    protected void activate() {
        log.info("Starting IDCO");
        appId = coreService.registerApplication("org.l2sm.vlinks.app");

        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(VLinkNetwork.class)
                .register(VLinkPathIntent.class)
                .register(ConnectPoint.class)
                .register(MacCompositeKey.class)
                .register(Port.class);

        networkStorage = storageService.<String, VLinkNetwork>consistentMapBuilder()
                .withName("vlinks-network-storage")
                .withApplicationId(appId)
                .withSerializer(Serializer.using(serializer.build()))
                .withPurgeOnUninstall()
                .build();

        connectionPointStorage = storageService.<ConnectPoint, Port>consistentMapBuilder()
                .withName("vlinks-connection-point-storage")
                .withApplicationId(appId)
                .withSerializer(Serializer.using(serializer.build()))
                .withPurgeOnUninstall()
                .build();

        macStorage = storageService.<MacCompositeKey, ConnectPoint>consistentMapBuilder()
                .withName("vlinks-mac-storage")
                .withApplicationId(appId)
                .withSerializer(Serializer.using(serializer.build()))
                .withPurgeOnUninstall()
                .build();

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
            Thread.currentThread().interrupt();
        }

        log.info("Withdrawing all the intents");
        intentService.getIntents().forEach(intent -> {
            if (intent.appId().equals(appId)) {
                intentService.withdraw(intent);
                intentService.purge(intent);
            }
        });

        log.info("Clearing database");
        networkStorage.clear();
        connectionPointStorage.clear();
        macStorage.clear();
        intentService.removeListener(intentListener);

        log.info("IDCO has stopped");
    }

    @Override
    public void createVLinkNetwork(String networkVlinkId, ConnectPoint networkVlinkFromEndpoint,
                                   ConnectPoint networkVlinkToEndpoint, String[] vLinkPath)
            throws IDCOVLinkServiceException {

        genericEventHandler.submit(() -> {
            log.info("Creating network: {}", networkVlinkId);
            if (networkStorage.containsKey(networkVlinkId)) {
                log.warn("Network {} already exists", networkVlinkId);
                return;
            }

            Long tunnelId = tunnelIdProvider.getNewId();
            if (tunnelId == null) {
                log.error("Unable to allocate tunnel id for network {}", networkVlinkId);
                return;
            }

            VLinkNetwork network = new VLinkNetwork(networkVlinkId);
            network.getVLinkNetworkEndpoints().add(networkVlinkFromEndpoint);
            network.getVLinkNetworkEndpoints().add(networkVlinkToEndpoint);
            network.getIds().add(tunnelId);
            network.getIds().add(tunnelId);
            networkStorage.put(networkVlinkId, network);

            connectionPointStorage.put(networkVlinkFromEndpoint, new Port(networkVlinkId, tunnelId));
            connectionPointStorage.put(networkVlinkToEndpoint, new Port(networkVlinkId, tunnelId));

            Key intentKey = Key.of("idco-main-" + networkVlinkId, appId);
            Intent intent = VLinkPathIntent.builder()
                    .key(intentKey)
                    .appId(appId)
                    .one(networkVlinkFromEndpoint)
                    .two(networkVlinkToEndpoint)
                    .path(vLinkPath)
                    .priority(VIRTUAL_LINK_PRIORITY)
                    .tunnelID(tunnelId)
                    .build();

            intentService.submit(intent);
            networkStorage.compute(networkVlinkId, (key, oldNetwork) -> {
                oldNetwork.getIntents().add(intentKey);
                return oldNetwork;
            });

            log.info("The network {} from {} to {} was correctly created",
                    networkVlinkId, networkVlinkFromEndpoint, networkVlinkToEndpoint);
        });
    }

    @Override
    public void deleteVLinkNetwork(String networkVlinkId) throws IDCOVLinkServiceException {
        genericEventHandler.submit(() -> {
            log.info("Deleting network {}", networkVlinkId);
            if (!networkStorage.containsKey(networkVlinkId)) {
                log.info("Network {} does not exist", networkVlinkId);
                return;
            }

            VLinkNetwork network = networkStorage.get(networkVlinkId).value();
            network.getIntents().forEach(intentKey -> {
                Intent intent = intentService.getIntent(intentKey);
                if (intent != null) {
                    intentService.withdraw(intent);
                }
            });

            network.getVLinkNetworkEndpoints().forEach(connectionPointStorage::remove);

            Set<MacCompositeKey> macKeysToRemove = macStorage.keySet().stream()
                    .filter(k -> k.networkId.equals(networkVlinkId))
                    .collect(Collectors.toSet());
            macKeysToRemove.forEach(macStorage::remove);

            networkStorage.remove(networkVlinkId);
            log.info("The network with id \"{}\" has been deleted", networkVlinkId);
        });
    }

    @Override
    public VLinkNetwork getVLinkNetwork(String networkVlinkId) throws IDCOVLinkServiceException {
        Future<VLinkNetwork> future = genericEventHandler.submit(() -> {
            log.info("Retrieving network {}", networkVlinkId);
            if (!networkStorage.containsKey(networkVlinkId)) {
                return null;
            }
            return networkStorage.get(networkVlinkId).value().clone();
        });

        try {
            return future.get();
        } catch (Exception e) {
            return null;
        }
    }

    class ArpProxyPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            if (context == null || context.isHandled()) {
                return;
            }

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
            MacCompositeKey macKey = new MacCompositeKey(mscsId, dstMac);

            if (!(dstMac.isBroadcast() || dstMac.isMulticast()) && macStorage.containsKey(macKey)) {
                ConnectPoint hostLocation = macStorage.get(macKey).value();
                TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(hostLocation.port()).build();
                OutboundPacket outboundPacket = new DefaultOutboundPacket(hostLocation.deviceId(), treatment,
                        context.inPacket().unparsed());
                packetService.emit(outboundPacket);
                context.block();
                return;
            }

            Collection<ConnectPoint> connectPoints = Collections.emptySet();
            if (networkStorage.containsKey(mscsId)) {
                connectPoints = networkStorage.get(mscsId).value().getVLinkNetworkEndpoints().stream()
                        .filter(p -> !p.equals(heardPort))
                        .collect(Collectors.toSet());
            }

            connectPoints.forEach(point -> {
                TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(point.port()).build();
                OutboundPacket outboundPacket = new DefaultOutboundPacket(point.deviceId(), treatment,
                        context.inPacket().unparsed());
                packetService.emit(outboundPacket);
            });

            context.block();
            log.info("Proxying packet for: {} in network {}", dstMac, mscsId);
        }
    }

    private class VNFLocationProvider implements PacketProcessor {

        private void requestIntercepts() {
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP);
            packetService.requestPackets(selector.build(), ARP_TO_CONTROLLER_PRIORITY, appId);
        }

        private void withdrawIntercepts() {
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP);
            packetService.cancelPackets(selector.build(), ARP_TO_CONTROLLER_PRIORITY, appId);
        }

        @Override
        public void process(PacketContext context) {
            if (context == null) {
                return;
            }

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

            if (heardOn.port().isLogical()) {
                return;
            }

            MacAddress hostId = eth.getSourceMAC();
            if (eth.getEtherType() == Ethernet.TYPE_ARP) {
                detectedHost(hostId, heardOn, context);
            }
        }

        public void detectedHost(MacAddress macAddress, ConnectPoint hostLocation, PacketContext context) {
            if (!connectionPointStorage.containsKey(hostLocation)) {
                return;
            }

            String mscsId = connectionPointStorage.get(hostLocation).value().getNetworkId();
            log.info("New packet received: {} for network {}", macAddress, mscsId);

            MacCompositeKey macKey = new MacCompositeKey(mscsId, macAddress);
            if (macStorage.containsKey(macKey)) {
                ConnectPoint lastLocation = macStorage.get(macKey).value();
                if (!lastLocation.equals(hostLocation)) {
                    log.warn("The host {} in network {} has changed its location. Host mobility is not supported",
                            macAddress, mscsId);
                }
                return;
            }

            Long tunnelId = connectionPointStorage.get(hostLocation).value().getTunnelId();
            if (tunnelId == null) {
                context.block();
                return;
            }

            Collection<ConnectPoint> connectPoints = Collections.emptySet();
            if (networkStorage.containsKey(mscsId)) {
                connectPoints = networkStorage.get(mscsId).value().getVLinkNetworkEndpoints().stream()
                        .filter(p -> !p.equals(hostLocation))
                        .collect(Collectors.toSet());
            }

            List<FlowRule> rules = connectPoints.stream()
                    .map(point -> createRule(macAddress, hostLocation, point, tunnelId))
                    .collect(Collectors.toList());

            Key intentKey = generateHostIntentKey(macAddress, mscsId);
            FlowRuleIntent ruleIntent = new FlowRuleIntent(appId, intentKey, rules,
                    Collections.emptyList(), PathIntent.ProtectionType.PRIMARY, null);

            intentService.submit(ruleIntent);
            networkStorage.compute(mscsId, (key, oldNetwork) -> {
                oldNetwork.getIntents().add(intentKey);
                return oldNetwork;
            });
            macStorage.put(macKey, hostLocation);
        }

        private FlowRule createRule(MacAddress address, ConnectPoint cp, ConnectPoint otherCp, long tunnelId) {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder().setTunnelId(tunnelId).transition(1).build();
            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthDst(address)
                    .matchInPort(otherCp.port())
                    .build();
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
            log.info("Intent event: {}", event.type().name());
            switch (event.type()) {
                case FAILED:
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

    private Key generateHostIntentKey(MacAddress hostMac, String mscsId) {
        return Key.of("idco-host-" + mscsId + "-" + hostMac, appId);
    }

    static class TunnelIdProvider {
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
            if (count == modulus) {
                return null;
            }

            long id = lastId;
            this.lastId = (lastId * a + c) % modulus;
            count++;
            return id;
        }

        private class IdGenerator extends SecureRandom {
            public long nextId() {
                return next(24);
            }
        }
    }

    private static class Port {
        private String networkId;
        private Long tunnelId;

        Port(String networkId, Long tunnelId) {
            this.networkId = networkId;
            this.tunnelId = tunnelId;
        }

        String getNetworkId() {
            return networkId;
        }

        Long getTunnelId() {
            return tunnelId;
        }
    }

    private static class MacCompositeKey {
        private final String networkId;
        private final MacAddress mac;

        MacCompositeKey(String networkId, MacAddress mac) {
            this.networkId = networkId;
            this.mac = mac;
        }

        @Override
        public int hashCode() {
            return Objects.hash(networkId, mac);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MacCompositeKey other = (MacCompositeKey) obj;
            return Objects.equals(networkId, other.networkId) && Objects.equals(mac, other.mac);
        }
    }
}
