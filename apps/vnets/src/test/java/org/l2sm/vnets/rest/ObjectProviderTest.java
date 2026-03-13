package org.l2sm.vnets.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;

import org.junit.Test;
import org.l2sm.vnets.dto.NetworkDTO;

public class ObjectProviderTest {

    private final ObjectProvider provider = new ObjectProvider();

    @Test
    public void isReadableAlwaysTrue() {
        assertTrue(provider.isReadable(Object.class, Object.class, new java.lang.annotation.Annotation[0], MediaType.APPLICATION_JSON_TYPE));
    }

    @Test
    public void isWriteableAlwaysTrue() {
        assertTrue(provider.isWriteable(Object.class, Object.class, new java.lang.annotation.Annotation[0], MediaType.APPLICATION_JSON_TYPE));
    }

    @Test
    public void readFromJsonParsesNetworkDto() throws Exception {
        String json = "{\"networkId\":\"net1\",\"networkEndpoints\":[\"of:0000000000000001/1\"],\"tunnelList\":[11],\"mirrorPort\":\"of:0000000000000009/9\"}";

        NetworkDTO dto = (NetworkDTO) provider.readFrom(asObjectClass(NetworkDTO.class), NetworkDTO.class,
                new java.lang.annotation.Annotation[0], MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals("net1", dto.getNetworkId());
        assertEquals(1, dto.getNetworkEndpoints().size());
        assertEquals(Long.valueOf(11), dto.getTunnelList().get(0));
        assertEquals("of:0000000000000009/9", dto.getMirrorPort());
    }

    @Test
    public void readFromYamlParsesNetworkDto() throws Exception {
        String yaml = "networkId: net2\nnetworkEndpoints:\n  - of:0000000000000001/2\ntunnelList:\n  - 12\nmirrorPort: of:0000000000000009/9\n";

        NetworkDTO dto = (NetworkDTO) provider.readFrom(asObjectClass(NetworkDTO.class), NetworkDTO.class,
                new java.lang.annotation.Annotation[0], new MediaType("application", "yaml"),
                new MultivaluedHashMap<>(), new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals("net2", dto.getNetworkId());
        assertEquals("of:0000000000000001/2", dto.getNetworkEndpoints().get(0));
        assertEquals(Long.valueOf(12), dto.getTunnelList().get(0));
        assertEquals("of:0000000000000009/9", dto.getMirrorPort());
    }

    @Test
    public void readFromJsonUnknownPropertyReturnsBadRequest() {
        String json = "{\"networkId\":\"net1\",\"unexpected\":1}";

        try {
            provider.readFrom(asObjectClass(NetworkDTO.class), NetworkDTO.class,
                    new java.lang.annotation.Annotation[0], MediaType.APPLICATION_JSON_TYPE,
                    new MultivaluedHashMap<>(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        } catch (WebApplicationException e) {
            assertEquals(400, e.getResponse().getStatus());
            return;
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        throw new AssertionError("Expected WebApplicationException");
    }

    @Test
    public void readFromMalformedJsonReturnsBadRequest() {
        String json = "{\"networkId\":\"net1\"";

        try {
            provider.readFrom(asObjectClass(NetworkDTO.class), NetworkDTO.class,
                    new java.lang.annotation.Annotation[0], MediaType.APPLICATION_JSON_TYPE,
                    new MultivaluedHashMap<>(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        } catch (WebApplicationException e) {
            assertEquals(400, e.getResponse().getStatus());
            return;
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        throw new AssertionError("Expected WebApplicationException");
    }

    @Test
    public void writeToJsonTypeProducesSerializablePayload() throws Exception {
        NetworkDTO dto = dto("net-json", "of:0000000000000001/1", 55L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        provider.writeTo(dto, NetworkDTO.class, NetworkDTO.class, new java.lang.annotation.Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MultivaluedHashMap<>(), out);

        String payload = out.toString(StandardCharsets.UTF_8.name());
        assertTrue(payload.contains("net-json"));
        assertTrue(payload.contains("networkEndpoints"));
        assertTrue(payload.contains("tunnelList"));
        assertTrue(payload.contains("mirrorPort"));
    }

    @Test
    public void writeToYamlSerializesDto() throws Exception {
        NetworkDTO dto = dto("net-yaml", "of:0000000000000001/2", 66L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        provider.writeTo(dto, NetworkDTO.class, NetworkDTO.class, new java.lang.annotation.Annotation[0],
                new MediaType("application", "yaml"), new MultivaluedHashMap<>(), out);

        String payload = out.toString(StandardCharsets.UTF_8.name());
        assertTrue(payload.contains("networkId: \"net-yaml\""));
        assertTrue(payload.contains("networkEndpoints"));
        assertTrue(payload.contains("mirrorPort"));
    }

    @Test
    public void readFromTreatsNonJsonMediaAsYaml() throws Exception {
        String yaml = "networkId: net3\n";

        NetworkDTO dto = (NetworkDTO) provider.readFrom(asObjectClass(NetworkDTO.class), NetworkDTO.class,
                new java.lang.annotation.Annotation[0], MediaType.TEXT_PLAIN_TYPE,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals("net3", dto.getNetworkId());
    }

    @Test
    public void roundTripJsonKeepsValues() throws Exception {
        NetworkDTO original = dto("net-roundtrip", "of:0000000000000009/9", 99L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        provider.writeTo(original, NetworkDTO.class, NetworkDTO.class, new java.lang.annotation.Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MultivaluedHashMap<>(), out);

        NetworkDTO parsed = (NetworkDTO) provider.readFrom(asObjectClass(NetworkDTO.class), NetworkDTO.class,
                new java.lang.annotation.Annotation[0], MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream(out.toByteArray()));

        assertNotNull(parsed);
        assertEquals("net-roundtrip", parsed.getNetworkId());
        assertEquals("of:0000000000000009/9", parsed.getNetworkEndpoints().get(0));
        assertEquals(Long.valueOf(99), parsed.getTunnelList().get(0));
        assertEquals("of:0000000000000010/10", parsed.getMirrorPort());
    }

    private static NetworkDTO dto(String id, String endpoint, long tunnel) {
        NetworkDTO dto = new NetworkDTO();
        dto.setNetworkId(id);
        ArrayList<String> endpoints = new ArrayList<>();
        endpoints.add(endpoint);
        dto.setNetworkEndpoints(endpoints);
        ArrayList<Long> tunnels = new ArrayList<>();
        tunnels.add(tunnel);
        dto.setTunnelList(tunnels);
        dto.setMirrorPort("of:0000000000000010/10");
        return dto;
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> asObjectClass(Class<?> cls) {
        return (Class<Object>) cls;
    }
}
