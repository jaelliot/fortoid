package org.kerifoundation.fortandroid

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadScriptContractTest {

    @Test
    fun `sync payload script defaults to the FortWeb path and validates Android output`() {
        val script = Files.readString(locateSyncScript())

        assertTrue(
            "sync-payload.sh should default PAYLOAD_SOURCE to fortweb",
            script.contains("PAYLOAD_SOURCE=\"\${PAYLOAD_SOURCE:-fortweb}\"")
        )
        assertTrue(
            "sync-payload.sh should write the FortWeb redirect root",
            script.contains("window.location.replace('./fortweb/app/index.html');")
        )
        assertTrue(
            "sync-payload.sh should validate the final Android payload",
            script.contains("--target android-asset-payload")
        )
        assertTrue(
            "sync-payload.sh should refresh the generated BridgeContract.kt",
            script.contains("cp \"\${BRIDGE_CONTRACT_SRC}\" \"\${BRIDGE_CONTRACT_DEST}\"")
        )
        assertTrue(
            "sync-payload.sh should keep both fort-ios and fortweb source cases explicit",
            script.contains("fort-ios)") && script.contains("fortweb)")
        )
        assertTrue(
            "sync-payload.sh should verify the final manifest and bridge artifacts",
            script.contains("expected build-manifest.json missing after sync")
                && script.contains("expected BridgeContract.kt missing after sync")
        )
    }

    private fun locateSyncScript(): Path {
        val candidatePaths = listOf(
            Paths.get(System.getProperty("user.dir"), "sync-payload.sh"),
            Paths.get(System.getProperty("user.dir"), "..", "sync-payload.sh")
        )

        return candidatePaths.firstOrNull { Files.isRegularFile(it) }
            ?: throw IllegalStateException(
                "Unable to locate sync-payload.sh from ${System.getProperty("user.dir")}"
            )
    }
}