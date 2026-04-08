package org.l2sm.vnets.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.intent.Key;




public class Network {


    public String networkId;
    public List<ConnectPoint> networkEndpoints;
    public List<Long> tunnelIds;
    private ConnectPoint mirrorPort;
    private Set<Key> intents;
    

    public Network() {

        this.networkEndpoints = new ArrayList<>();
        this.tunnelIds = new ArrayList<>();
        this.intents = new  HashSet<>();
    }

    public Network(String networkId) {
        this(networkId, null);
    }

    public Network(String networkId, ConnectPoint mirrorPort) {
        this.networkId = networkId;
        this.mirrorPort = mirrorPort;
        this.networkEndpoints = new ArrayList<>();
        this.tunnelIds = new ArrayList<>();
        this.intents = new  HashSet<>();
    }


 
    public Set<Key> getIntents() {
        return intents;
    }

    public void setIntents(Set<Key> intents) {
        this.intents = intents;
    }

    public List<ConnectPoint> getNetworkEndpoints() {
        return networkEndpoints;
    }

    public String getNetworkId() {
        return networkId;
    }

    public List<Long> getIds() {
        return tunnelIds;
    }

    public ConnectPoint getMirrorPort() {
        return mirrorPort;
    }

    public void setMirrorPort(ConnectPoint mirrorPort) {
        this.mirrorPort = mirrorPort;
    }

    public Network clone(){
        Network newNetwork = new Network();
        newNetwork.networkId = networkId;
        newNetwork.networkEndpoints.addAll(networkEndpoints);
        newNetwork.tunnelIds.addAll(tunnelIds);
        newNetwork.mirrorPort = mirrorPort;
        return newNetwork;
    }

    public String toString(){
        StringBuffer buffer = new StringBuffer();
        buffer.append("Id: " + networkId + "\n");
        buffer.append("Endpoints: \n");
        for (ConnectPoint c: networkEndpoints){
            buffer.append(" -" + c + "\n"); 
        }
        buffer.append("Tunnel Ids: \n");
        for (Long l: tunnelIds){
            buffer.append(" -" + l + "\n"); 
        }
        buffer.append("Mirror Port: " + mirrorPort + "\n");
        return buffer.toString();
    }

}
