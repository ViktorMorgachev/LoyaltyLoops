package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.RefreshTokenRequest
import io.loyaltyloop.shared.models.ResetPinRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartnerConstraintsTest {

    @Test
    fun `owner cannot create duplicate partner`() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val owner = client.registerAndLogin()

        val first = client.post("/partners/create") {
            header("Authorization", "Bearer ${owner.accessToken}")
            header("X-Timezone-Id", "Asia/Bishkek")
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest(businessName = "First Biz", ownerPin = "1234", baseCurrency = "KGS"))
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val second = client.post("/partners/create") {
            header("Authorization", "Bearer ${owner.accessToken}")
            header("X-Timezone-Id", "Asia/Bishkek")
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest(businessName = "Second Biz", ownerPin = "1234", baseCurrency = "KGS"))
        }
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals(AppErrorCode.BUSINESS_ALREADY_EXISTS, second.body<ApiMessage>().code)
    }

    @Test
    fun `pin reset freezes account`() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val owner = client.registerAndLogin()

        val created = client.post("/partners/create") {
            header("Authorization", "Bearer ${owner.accessToken}")
            header("X-Timezone-Id", "Asia/Bishkek")
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest(businessName = "Freeze Biz", ownerPin = "1234", baseCurrency = "KGS"))
        }
        assertEquals(HttpStatusCode.Created, created.status)

        // Свежий AuthResponse: воркспейс созданного партнера появляется после ротации токена
        val refreshResponse = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(owner.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val auth = refreshResponse.body<AuthResponse>()
        assertTrue(auth.workspaces.isNotEmpty(), "Owner must see created partner workspace")
        val workspaceId = auth.workspaces.first().id

        val reset = client.post("/partners/pin/reset") {
            header("Authorization", "Bearer ${auth.accessToken}")
            header("X-Workspace-Id", workspaceId)
            contentType(ContentType.Application.Json)
            setBody(ResetPinRequest(confirm = true))
        }
        assertEquals(HttpStatusCode.OK, reset.status)

        // Аккаунт заморожен: следующее мутирующее действие отклоняется
        val whileFrozen = client.post("/partners/pin/reset") {
            header("Authorization", "Bearer ${auth.accessToken}")
            header("X-Workspace-Id", workspaceId)
            contentType(ContentType.Application.Json)
            setBody(ResetPinRequest(confirm = true))
        }
        assertEquals(HttpStatusCode.Forbidden, whileFrozen.status)
        assertEquals(AppErrorCode.ACCOUNT_FROZEN, whileFrozen.body<ApiMessage>().code)
    }
}
