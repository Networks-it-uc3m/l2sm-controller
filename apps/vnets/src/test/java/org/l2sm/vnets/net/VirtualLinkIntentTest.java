package org.l2sm.vnets.net;

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
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.Intent;

public class VirtualLinkIntentTest {
    private static final IdGenerator ID_GENERATOR = new IdGenerator() {
        private long current = 1;

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
    public void builderSetsAllConfiguredFields() {
        ApplicationId appId = appId();
        Key key = Key.of("vl-1", appId);
        ConnectPoint one = cp("of:0000000000000001/1");
        ConnectPoint two = cp("of:0000000000000002/2");

        VirtualLinkIntent intent = VirtualLinkIntent.builder()
                .appId(appId)
                .key(key)
                .one(one)
                .two(two)
                .priority(300)
                .tunnelID(700L)
                .build();

        assertEquals(appId, intent.appId());
        assertEquals(key, intent.key());
        assertEquals(one, intent.one());
        assertEquals(two, intent.two());
        assertEquals(300, intent.priority());
        assertEquals(700L, intent.tunnelId());
    }

    @Test
    public void builderCanCreateIntentWithoutConnectPoints() {
        VirtualLinkIntent intent = VirtualLinkIntent.builder()
                .appId(appId())
                .key(Key.of("vl-2", appId()))
                .priority(100)
                .tunnelID(11L)
                .build();

        assertNull(intent.one());
        assertNull(intent.two());
        assertEquals(11L, intent.tunnelId());
    }

    @Test
    public void builderDefaultsTunnelIdWhenNotSet() {
        VirtualLinkIntent intent = VirtualLinkIntent.builder()
                .appId(appId())
                .key(Key.of("vl-3", appId()))
                .priority(123)
                .one(cp("of:0000000000000001/1"))
                .two(cp("of:0000000000000002/2"))
                .build();

        assertEquals(-1L, intent.tunnelId());
    }

    @Test
    public void builderSupportsCustomPriority() {
        VirtualLinkIntent intent = VirtualLinkIntent.builder()
                .appId(appId())
                .key(Key.of("vl-4", appId()))
                .one(cp("of:0000000000000001/1"))
                .two(cp("of:0000000000000002/2"))
                .priority(999)
                .tunnelID(1L)
                .build();

        assertEquals(999, intent.priority());
    }

    @Test
    public void serializerConstructorUsesSentinelValues() {
        VirtualLinkIntent intent = new VirtualLinkIntent();

        assertNull(intent.one());
        assertNull(intent.two());
        assertEquals(-1L, intent.tunnelId());
    }

    @Test
    public void toStringContainsCoreFields() {
        ApplicationId appId = appId();
        VirtualLinkIntent intent = VirtualLinkIntent.builder()
                .appId(appId)
                .key(Key.of("vl-5", appId))
                .one(cp("of:0000000000000001/1"))
                .two(cp("of:0000000000000002/2"))
                .priority(100)
                .tunnelID(44L)
                .build();

        String rendered = intent.toString();

        assertTrue(rendered.contains("key"));
        assertTrue(rendered.contains("priority"));
        assertTrue(rendered.contains("tunnelID"));
        assertTrue(rendered.contains("44"));
    }

    @Test
    public void builderProducesDistinctInstances() {
        ApplicationId appId = appId();
        Key key = Key.of("vl-6", appId);

        VirtualLinkIntent a = VirtualLinkIntent.builder()
                .appId(appId)
                .key(key)
                .one(cp("of:0000000000000001/1"))
                .two(cp("of:0000000000000002/2"))
                .priority(100)
                .tunnelID(1L)
                .build();

        VirtualLinkIntent b = VirtualLinkIntent.builder()
                .appId(appId)
                .key(key)
                .one(cp("of:0000000000000001/1"))
                .two(cp("of:0000000000000002/2"))
                .priority(100)
                .tunnelID(1L)
                .build();

        assertNotNull(a.id());
        assertNotNull(b.id());
        assertTrue(!a.equals(b));
    }

    private static ConnectPoint cp(String value) {
        return ConnectPoint.deviceConnectPoint(value);
    }

    private static ApplicationId appId() {
        return new ApplicationId() {
            @Override
            public short id() {
                return 10;
            }

            @Override
            public String name() {
                return "test.app";
            }
        };
    }
}
