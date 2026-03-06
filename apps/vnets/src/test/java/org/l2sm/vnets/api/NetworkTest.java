package org.l2sm.vnets.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.intent.Key;

public class NetworkTest {

    @Test
    public void defaultConstructorInitializesCollections() {
        Network network = new Network();

        assertNotNull(network.getNetworkEndpoints());
        assertNotNull(network.getIds());
        assertNotNull(network.getIntents());
        assertTrue(network.getNetworkEndpoints().isEmpty());
        assertTrue(network.getIds().isEmpty());
        assertTrue(network.getIntents().isEmpty());
    }

    @Test
    public void constructorWithIdSetsId() {
        Network network = new Network("net-a");

        assertEquals("net-a", network.getNetworkId());
    }

    @Test
    public void setIntentsReplacesIntentSet() {
        Network network = new Network("net-a");
        Set<Key> intents = new HashSet<>();
        intents.add(Key.of("k1", appId()));

        network.setIntents(intents);

        assertEquals(1, network.getIntents().size());
        assertTrue(network.getIntents().containsAll(intents));
    }

    @Test
    public void cloneCopiesScalarAndLists() {
        Network network = new Network("net-a");
        network.getNetworkEndpoints().add(cp("of:0000000000000001/1"));
        network.getIds().add(101L);
        network.getIntents().add(Key.of("k1", appId()));

        Network cloned = network.clone();

        assertEquals("net-a", cloned.getNetworkId());
        assertEquals(1, cloned.getNetworkEndpoints().size());
        assertEquals(1, cloned.getIds().size());
        assertTrue(cloned.getIntents().isEmpty());
    }

    @Test
    public void cloneProducesIndependentCollections() {
        Network network = new Network("net-a");
        network.getNetworkEndpoints().add(cp("of:0000000000000001/1"));
        network.getIds().add(100L);

        Network cloned = network.clone();
        cloned.getNetworkEndpoints().add(cp("of:0000000000000002/2"));
        cloned.getIds().add(200L);

        assertEquals(1, network.getNetworkEndpoints().size());
        assertEquals(1, network.getIds().size());
        assertEquals(2, cloned.getNetworkEndpoints().size());
        assertEquals(2, cloned.getIds().size());
    }

    @Test
    public void cloneCreatesDifferentObject() {
        Network network = new Network("net-a");

        Network cloned = network.clone();

        assertNotSame(network, cloned);
        assertNotSame(network.getNetworkEndpoints(), cloned.getNetworkEndpoints());
        assertNotSame(network.getIds(), cloned.getIds());
    }

    @Test
    public void toStringContainsIdEndpointsAndTunnelIds() {
        Network network = new Network("net-a");
        network.getNetworkEndpoints().add(cp("of:0000000000000001/1"));
        network.getIds().add(200L);

        String rendered = network.toString();

        assertTrue(rendered.contains("Id: net-a"));
        assertTrue(rendered.contains("Endpoints:"));
        assertTrue(rendered.contains("Tunnel Ids:"));
        assertTrue(rendered.contains("of:0000000000000001/1"));
        assertTrue(rendered.contains("200"));
    }

    @Test
    public void networkAllowsMultipleEndpoints() {
        Network network = new Network("net-a");

        network.getNetworkEndpoints().add(cp("of:0000000000000001/1"));
        network.getNetworkEndpoints().add(cp("of:0000000000000002/2"));

        assertEquals(2, network.getNetworkEndpoints().size());
    }

    @Test
    public void networkAllowsMultipleTunnelIds() {
        Network network = new Network("net-a");

        network.getIds().add(100L);
        network.getIds().add(101L);

        assertEquals(2, network.getIds().size());
    }

    @Test
    public void intentSetCanBeMutatedThroughGetter() {
        Network network = new Network("net-a");

        network.getIntents().add(Key.of("k1", appId()));

        assertFalse(network.getIntents().isEmpty());
    }

    private static ConnectPoint cp(String value) {
        return ConnectPoint.deviceConnectPoint(value);
    }

    private static ApplicationId appId() {
        return new ApplicationId() {
            @Override
            public short id() {
                return 1;
            }

            @Override
            public String name() {
                return "test.app";
            }
        };
    }
}
