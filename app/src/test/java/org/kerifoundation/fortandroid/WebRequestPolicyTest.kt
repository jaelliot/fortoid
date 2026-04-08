package org.kerifoundation.fortandroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRequestPolicyTest {
    @Test
    fun trustedPayloadPartsAreRecognized() {
        assertTrue(
            WebRequestPolicy.isTrustedPayloadParts(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = "/assets/payload/index.html"
            )
        )
    }

    @Test
    fun offOriginSubresourceIsBlocked() {
        assertTrue(
            WebRequestPolicy.shouldBlockSubresource(
                uri = null,
                isMainFrame = false
            )
        )
    }

    @Test
    fun sameHostOutsideAssetsPrefixIsNotTrustedPayload() {
        assertFalse(
            WebRequestPolicy.isTrustedPayloadParts(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = "/other/runtime.js"
            )
        )
    }

    @Test
    fun topLevelNavigationIsNotBlockedBySubresourcePolicy() {
        assertFalse(WebRequestPolicy.shouldBlockSubresource(uri = null, isMainFrame = true))
    }

    @Test
    fun trustedPayloadPartsRequireExactOriginAndAssetsPrefix() {
        assertTrue(
            WebRequestPolicy.isTrustedPayloadParts(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = "/assets/payload/index.html"
            )
        )
        assertFalse(
            WebRequestPolicy.isTrustedPayloadParts(
                scheme = "http",
                host = "appassets.androidplatform.net",
                path = "/assets/payload/index.html"
            )
        )
        assertFalse(
            WebRequestPolicy.isTrustedPayloadParts(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = "/other/index.html"
            )
        )
        assertFalse(
            WebRequestPolicy.isTrustedPayloadParts(
                scheme = "https",
                host = "appassets.androidplatform.net.evil",
                path = "/assets/payload/index.html"
            )
        )
    }

    @Test
    fun trustedPayloadPartsRequireNonNullPath() {
        assertFalse(
            WebRequestPolicy.isTrustedPayloadParts(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = null
            )
        )
    }

    @Test
    fun bridgeOriginRequiresTrustedSchemeAndHost() {
        assertTrue(
            WebRequestPolicy.isTrustedBridgeParts(
                scheme = "https",
                host = "appassets.androidplatform.net"
            )
        )
        assertFalse(
            WebRequestPolicy.isTrustedBridgeParts(
                scheme = "https",
                host = "example.com"
            )
        )
    }

    @Test
    fun trustedBridgePartsRequireExactOrigin() {
        assertTrue(
            WebRequestPolicy.isTrustedBridgeParts(
                scheme = "https",
                host = "appassets.androidplatform.net"
            )
        )
        assertFalse(
            WebRequestPolicy.isTrustedBridgeParts(
                scheme = "http",
                host = "appassets.androidplatform.net"
            )
        )
        assertFalse(
            WebRequestPolicy.isTrustedBridgeParts(
                scheme = "https",
                host = "appassets.androidplatform.net.evil"
            )
        )
    }
}