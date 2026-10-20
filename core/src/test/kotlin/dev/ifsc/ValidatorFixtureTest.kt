package dev.ifsc

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports the upstream `tests/validator_asserts.json` fixture. Keeping this test
 * ensures the Kotlin validator matches the reference implementations.
 */
class ValidatorFixtureTest {

    @Test
    fun matchesUpstreamFixture() {
        val resource = javaClass.getResourceAsStream("/validator_asserts.json")
            ?: error("validator_asserts.json is missing from test resources")
        val root = JSONObject(resource.bufferedReader().use { it.readText() })

        var checked = 0
        for (groupName in root.keys()) {
            val group = root.getJSONObject(groupName)
            for (code in group.keys()) {
                assertEquals(
                    "$groupName / $code",
                    group.getBoolean(code),
                    Ifsc.validate(code),
                )
                checked++
            }
        }
        assertTrue(checked > 500)
    }
}
