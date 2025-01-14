package org.l2sm.vnets.app;

import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.l2sm.vnets.api.Network;
import org.onlab.packet.MacAddress;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.intent.Key;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import org.slf4j.Logger;

public class IDCODatabase {

    private final Map<String, Network> networks = new ConcurrentHashMap<>();
    private final Map<String, Key> mainIntents = new ConcurrentHashMap<>();
    private final Map<String, Set<Key>> hostIntents = new ConcurrentHashMap<>();
    private final Map<ConnectPoint, String> portNetworks = Collections.synchronizedMap(new HashMap<>());
    private final Map<ConnectPoint, Long> portTunnelIds = new ConcurrentHashMap<>();
    private final Table<String, MacAddress, ConnectPoint> macTable = HashBasedTable.create();

    private final Logger log;

    public IDCODatabase(Logger log) {
        this.log = log;
    }


    public void addMainIntent(String networkId, Key intentKey) {
            mainIntents.put(networkId, intentKey);        
    }

    public boolean networkExists(String networkId) {
            return networks.containsKey(networkId);
    }

    public Network getNetwork(String networkId) {
            return networks.get(networkId).clone();
    }

    public void deleteNetwork(String networkId) {
            Network network = networks.get(networkId);
            for (ConnectPoint p : network.networkEndpoints) {
                portNetworks.remove(p);
                portTunnelIds.remove(p);
            }
            networks.remove(networkId);
            mainIntents.remove(networkId);
            hostIntents.remove(networkId);
            macTable.row(networkId).clear();
    }

    public void registerNetwork(String networkId) {
            Network network = new Network();
            network.networkId = networkId;
            networks.put(networkId, network);
            hostIntents.put(networkId, new HashSet<>());
    }

    public Collection<Key> getNetworkIntents(String networkId) {
            Set<Key> networkIntents = new HashSet<>(hostIntents.get(networkId));
            Key mainKey = mainIntents.get(networkId);
            if (mainKey != null) {
                networkIntents.add(mainKey);
            }
            return networkIntents;
    }

    public Iterable<Key> getAllIntents() {
        Set<Key> allIntents = new HashSet<>();
        for (String id : networks.keySet()) {
            allIntents.addAll(getNetworkIntents(id));
        }
        return allIntents;
    }

    public void cleanDatabases() {
            networks.clear();
            mainIntents.clear();
            hostIntents.clear();
            portNetworks.clear();
            portTunnelIds.clear();
            macTable.clear();
    }

    public void addPortToNetwork(String networkId, ConnectPoint networkEndpoint, Long tunnelId) {
            Network network = networks.get(networkId);
            network.networkEndpoints.add(networkEndpoint);
            network.tunnelIds.add(tunnelId);
            portNetworks.put(networkEndpoint, networkId);
            portTunnelIds.put(networkEndpoint, tunnelId);
    }

    public Collection<ConnectPoint> getPortsOfNetworkGivenPort(ConnectPoint heardPort) {
            String networkId = getNetworkIdForPort(heardPort);
            if (networkId == null) {
                return Collections.emptySet();
            }
            Network network = networks.get(networkId);
            return network.networkEndpoints.stream().filter(p -> !p.equals(heardPort)).collect(Collectors.toSet());
    }

    public String getNetworkIdForPort(ConnectPoint hostLocation) {
        return portNetworks.get(hostLocation);
    }

    public Long getTunnelIdOfPort(ConnectPoint hostLocation) {
        return portTunnelIds.get(hostLocation);
    }

    public ConnectPoint getHostLocation(String mscsId, MacAddress macAddress) {
        return macTable.get(mscsId, macAddress);
    }

    public void addIntentToNetwork(String mscsId, Key intentKey) {
            hostIntents.get(mscsId).add(intentKey);
    }

    public void setHostLocation(String mscsId, MacAddress macAddress, ConnectPoint hostLocation) {
            macTable.put(mscsId, macAddress, hostLocation);
    }
}
