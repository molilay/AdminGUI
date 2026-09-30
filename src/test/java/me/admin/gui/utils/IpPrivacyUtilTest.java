package me.admin.gui.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpPrivacyUtilTest {

    @Test
    void masksIpv4AfterNetworkPrefix() {
        assertEquals("192.168.x.x", IpPrivacyUtil.mask("192.168.10.25"));
    }

    @Test
    void masksIpv6ToNetworkPrefix() {
        assertEquals("2001:db8:1:2::/64", IpPrivacyUtil.mask("2001:db8:1:2:3:4:5:6"));
    }

    @Test
    void doesNotEchoMalformedInput() {
        assertEquals("hidden", IpPrivacyUtil.mask("secret-value"));
    }

    @Test
    void redactsIpLiteralsInsideAuditText() {
        assertEquals("from 10.20.x.x to 2001:db8:1:2::/64; case=42",
                IpPrivacyUtil.redactText("from 10.20.30.40 to 2001:db8:1:2:3:4:5:6; case=42"));
    }

    @Test
    void leavesInvalidAddressesAndTimesUntouched() {
        assertEquals("at 12:34:56, value=999.1.1.1",
                IpPrivacyUtil.redactText("at 12:34:56, value=999.1.1.1"));
    }
}
