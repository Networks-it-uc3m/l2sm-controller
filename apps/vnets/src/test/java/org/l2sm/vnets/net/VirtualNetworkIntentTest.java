package org.l2sm.vnets.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.IdGenerator;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.Key;

public class VirtualNetworkIntentTest {
    private static final IdGenerator ID_GENERATOR = new IdGenerator() {
        private long current = 1000;

        @Override
        public long getNewId() {
            return current++;
        }
    };

    @BeforeClass
    public static void bindIntentIdGenerator() {
        Intent.bindIdGenerator(ID_GENERATOR);
    }

    @AfterClass
    public static void unbindIntentIdGenerator() {
        Intent.unbindIdGenerator(ID_GENERATOR);
    }

    @Test
    public void builderSetsConfiguredFields() {
        ApplicationId appId = appId();
        ConnectPoint[] points = new ConnectPoint[]{
                cp("of:0000000000000001/1"),
                cp("of:0000000000000002/2")
        };
        long[] tunnels = new long[]{100L, 200L};
        ConnectPoint mirrorPort = cp("of:0000000000000003/3");

        VirtualNetworkIntent intent = VirtualNetworkIntent.builder()
                .appId(appId)
                .key(Key.of("vn-1", appId))
                .connectPoints(points)
                .tunnelIDs(tunnels)
                .mirrorPort(mirrorPort)
                .priority(450)
                .build();

        assertEquals(appId, intent.appId());
        assertEquals(450, intent.priority());
        assertArrayEquals(points, intent.connectPoints());
        assertArrayEquals(tunnels, intent.tunnelIds());
        assertEquals(mirrorPort, intent.mirrorPort());
    }

    @Test
    public void builderAllowsNullArrays() {
        VirtualNetworkIntent intent = VirtualNetworkIntent.builder()
                .appId(appId())
                .key(Key.of("vn-2", appId()))
                .priority(1)
                .build();

        assertNull(intent.connectPoints());
        assertNull(intent.tunnelIds());
        assertNull(intent.mirrorPort());
    }

    @Test
    public void serializerConstructorHasNullArrays() {
        VirtualNetworkIntent intent = new VirtualNetworkIntent();

        assertNull(intent.connectPoints());
        assertNull(intent.tunnelIds());
        assertNull(intent.mirrorPort());
    }

    @Test
    public void toStringContainsCoreFields() {
        ApplicationId appId = appId();
        VirtualNetworkIntent intent = VirtualNetworkIntent.builder()
                .appId(appId)
                .key(Key.of("vn-3", appId))
                .connectPoints(new ConnectPoint[]{cp("of:0000000000000001/1")})
                .tunnelIDs(new long[]{999L})
                .priority(33)
                .build();

        String rendered = intent.toString();

        assertTrue(rendered.contains("key"));
        assertTrue(rendered.contains("priority"));
        assertTrue(rendered.contains("tunnelIDs"));
        assertTrue(rendered.contains("mirrorPort"));
    }

    @Test
    public void builderKeepsArrayReferences() {
        ConnectPoint[] points = new ConnectPoint[]{cp("of:0000000000000001/1")};
        long[] tunnels = new long[]{1L};

        VirtualNetworkIntent intent = VirtualNetworkIntent.builder()
                .appId(appId())
                .key(Key.of("vn-4", appId()))
                .connectPoints(points)
                .tunnelIDs(tunnels)
                .priority(100)
                .build();

        points[0] = cp("of:0000000000000002/2");
        tunnels[0] = 2L;

        assertEquals(cp("of:0000000000000002/2"), intent.connectPoints()[0]);
        assertEquals(2L, intent.tunnelIds()[0]);
    }

    @Test
    public void builderCreatesDistinctInstances() {
        ApplicationId appId = appId();

        VirtualNetworkIntent first = VirtualNetworkIntent.builder()
                .appId(appId)
                .key(Key.of("vn-5", appId))
                .priority(10)
                .build();

        VirtualNetworkIntent second = VirtualNetworkIntent.builder()
                .appId(appId)
                .key(Key.of("vn-6", appId))
                .priority(10)
                .build();

        assertNotNull(first.id());
        assertNotNull(second.id());
        assertTrue(!first.equals(second));
    }

    private static ConnectPoint cp(String value) {
        return ConnectPoint.deviceConnectPoint(value);
    }

    private static ApplicationId appId() {
        return new ApplicationId() {
            @Override
            public short id() {
                return 20;
            }

            @Override
            public String name() {
                return "test.app";
            }
        };
    }
}
