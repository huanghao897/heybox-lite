package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HeyboxGatewayClientTest {
    @Test
    public void mapsOnlyAllowlistedReadOperations() {
        assertEquals("feed.list",
                HeyboxGatewayClient.operationFor(EndpointProvider.feeds()));
        assertEquals("post.detail",
                HeyboxGatewayClient.operationFor(EndpointProvider.linkTreeV2()));
        assertEquals("comment.list",
                HeyboxGatewayClient.operationFor(EndpointProvider.subComments()));
        assertEquals("search.links",
                HeyboxGatewayClient.operationFor(EndpointProvider.search()));
        assertEquals("",
                HeyboxGatewayClient.operationFor(EndpointProvider.favoriteLinks()));
        assertEquals("",
                HeyboxGatewayClient.operationFor(EndpointProvider.awardLink()));
    }

    @Test
    public void serverLegacySignatureVectorMatchesAndroidSigner() throws Exception {
        assertEquals("SWTID06", HeyboxSigner.legacySignatureFor(
                "/bbs/app/feeds",
                "1785168000",
                "0123456789ABCDEF0123456789ABCDEF"));
    }
}
