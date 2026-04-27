package org.kerifoundation.fortandroid

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadSnapshotContractTest {

    @Test
    fun `payload snapshot keeps FortWeb redirect root and required files`() {
        val payloadRoot = locatePayloadRoot()

        assertTrue("payload snapshot should exist", Files.isDirectory(payloadRoot))
        assertTrue("root index.html should exist", Files.isRegularFile(payloadRoot.resolve("index.html")))
        assertTrue(
            "FortWeb app entry should exist",
            Files.isRegularFile(payloadRoot.resolve("fortweb/app/index.html"))
        )
        assertTrue(
            "FortWeb runtime config should exist",
            Files.isRegularFile(payloadRoot.resolve("fortweb/pyscript-ci.toml"))
        )
        assertTrue(
            "FortWeb vendor tree should exist",
            Files.isDirectory(payloadRoot.resolve("fortweb/vendor"))
        )
        assertTrue(
            "FortWeb wheels tree should exist",
            Files.isDirectory(payloadRoot.resolve("fortweb/wheels"))
        )

        val rootIndex = Files.readString(payloadRoot.resolve("index.html"))
        assertTrue(
            "root index should redirect to the FortWeb app entry",
            rootIndex.contains("window.location.replace('./fortweb/app/index.html');")
        )
    }

    @Test
    fun `payload snapshot manifest declares the Android FortWeb contract`() {
        val payloadRoot = locatePayloadRoot()
        val manifest = Files.readString(payloadRoot.resolve("build-manifest.json"))

        assertEquals("fortweb-shared", extractJsonString(manifest, "producer"))
        assertEquals("product-shell", extractJsonString(manifest, "payload_profile"))
        assertEquals("fortweb/app/index.html", extractJsonString(manifest, "entry_document"))
        assertEquals("fortweb/app/app/main.js", extractJsonString(manifest, "entry_script"))
        assertEquals("pyscript-pyworker", extractJsonString(manifest, "pyodide_worker_mode"))
        assertEquals(
            "/fortweb/vendor/pyodide/0.29.3/pyodide.mjs",
            extractJsonString(manifest, "pyodide_asset_path")
        )
        assertEquals("esm", extractJsonString(manifest, "pyodide_asset_mode"))

        assertTrue(
            "manifest should declare the android asset payload sync target",
            manifest.contains("\"id\": \"android-asset-payload\"")
                && manifest.contains("\"path\": \"app/src/main/assets/payload\"")
        )
        assertTrue(
            "manifest should declare the iOS sync target so wrappers share one contract",
            manifest.contains("\"id\": \"ios-webpayload\"")
                && manifest.contains("\"path\": \"WebPayload\"")
        )
        assertTrue(
            "manifest should record a non-empty dist tree hash",
            extractJsonString(manifest, "dist_tree_sha256").isNotBlank()
        )
    }

    private fun locatePayloadRoot(): Path {
        val candidateRoots = listOf(
            Paths.get(System.getProperty("user.dir"), "app", "src", "main", "assets", "payload"),
            Paths.get(System.getProperty("user.dir"), "src", "main", "assets", "payload")
        )

        return candidateRoots.firstOrNull { Files.isDirectory(it) }
            ?: throw IllegalStateException(
                "Unable to locate app/src/main/assets/payload from ${System.getProperty("user.dir")}"
            )
    }

    private fun extractJsonString(json: String, key: String): String {
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        val match = pattern.find(json)
            ?: throw IllegalStateException("Missing JSON string field: $key")

        return match.groupValues[1]
    }
}