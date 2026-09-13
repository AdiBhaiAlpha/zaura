package com.example

import com.example.core.ai.OpenRouterConfig
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenRouterModelTest {

    @Test
    fun `test models before implementation, keeping 200 and removing non-200, ensuring default`() {
        val candidates = OpenRouterConfig.CURATED_MODELS

        var requestCounter = 0
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val code = when (requestCounter++) {
                    0 -> 200 // First model succeeds (200)
                    1 -> 404 // Second model fails
                    else -> 500 // Others fail
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body("{\"choices\":[]}".toResponseBody())
                    .build()
            }
            .build()

        val (filteredModels, defaultModelId) = OpenRouterConfig.testAndFilterModels(
            candidates = candidates,
            apiKey = "test-key",
            client = testClient
        )

        // Verify that working models (returning 200) are kept and non-200 models are removed
        assertFalse(filteredModels.isEmpty())
        assertTrue(filteredModels.any { it.id == candidates[0].id })
        assertFalse(filteredModels.any { it.id == candidates[1].id })

        // Verify that at least one model is found and set as default which returns 200
        assertNotNull(defaultModelId)
        assertTrue(filteredModels.any { it.id == defaultModelId })
    }
}
