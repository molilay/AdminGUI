package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoIpManagerTest {

    @Test
    void parsesExpectedProviderFields() {
        String json = """
                {"ip":"8.8.8.8","success":true,"type":"IPv4","country":"United States",
                 "country_code":"US","region":"California","city":"Mountain View",
                 "latitude":37.4,"longitude":-122.1,"timezone":{"id":"America/Los_Angeles"},
                 "connection":{"isp":"Google LLC","org":"Google Public DNS"}}
                """;
        GeoIpManager.GeoIpResult result = GeoIpManager.parse("8.8.8.8", json);
        assertEquals("United States", result.country());
        assertEquals("Mountain View", result.city());
        assertEquals("Google LLC", result.isp());
        assertEquals("America/Los_Angeles", result.timezone());
        assertEquals(37.4, result.latitude());
    }

    @Test
    void rejectsPrivateAndMalformedAddresses() {
        assertThrows(IllegalArgumentException.class, () -> GeoIpManager.normalizePublicIp("127.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> GeoIpManager.normalizePublicIp("192.168.1.25"));
        assertThrows(IllegalArgumentException.class, () -> GeoIpManager.normalizePublicIp("example.org"));
    }

    @Test
    void acceptsPublicNumericAddresses() {
        assertEquals("8.8.8.8", GeoIpManager.normalizePublicIp("8.8.8.8"));
    }

    @Test
    void parsesSecuritySignalsAndCalculatesDistance() {
        String firstJson = "{\"success\":true,\"latitude\":55.75,\"longitude\":37.61,\"proxy\":true,\"vpn\":false}";
        String secondJson = "{\"success\":true,\"latitude\":59.93,\"longitude\":30.31}";
        GeoIpManager.GeoIpResult first = GeoIpManager.parse("8.8.8.8", firstJson);
        GeoIpManager.GeoIpResult second = GeoIpManager.parse("1.1.1.1", secondJson);
        assertTrue(first.proxy());
        assertFalse(first.vpn());
        assertTrue(GeoIpManager.distanceKm(first, second) > 600);
    }
}
