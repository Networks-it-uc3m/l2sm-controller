package org.l2sm.vnets.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;

import org.junit.Test;

public class NetworkDTOTest {

    @Test
    public void propertiesDefaultToNull() {
        NetworkDTO dto = new NetworkDTO();

        assertNull(dto.getNetworkId());
        assertNull(dto.getNetworkEndpoints());
        assertNull(dto.getTunnelList());
    }

    @Test
    public void networkIdCanBeSetAndRead() {
        NetworkDTO dto = new NetworkDTO();

        dto.setNetworkId("net-1");

        assertEquals("net-1", dto.getNetworkId());
    }

    @Test
    public void networkEndpointsCanBeSetAndRead() {
        NetworkDTO dto = new NetworkDTO();
        ArrayList<String> endpoints = new ArrayList<>();
        endpoints.add("of:0000000000000001/1");

        dto.setNetworkEndpoints(endpoints);

        assertEquals(1, dto.getNetworkEndpoints().size());
        assertEquals("of:0000000000000001/1", dto.getNetworkEndpoints().get(0));
    }

    @Test
    public void tunnelListCanBeSetAndRead() {
        NetworkDTO dto = new NetworkDTO();
        ArrayList<Long> tunnels = new ArrayList<>();
        tunnels.add(123L);

        dto.setTunnelList(tunnels);

        assertEquals(1, dto.getTunnelList().size());
        assertEquals(Long.valueOf(123L), dto.getTunnelList().get(0));
    }
}
