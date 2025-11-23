package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.ResetPinRequest
import io.loyaltyloop.shared.models.UpdatePinRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class PartnerConstraintsTest {

    @Test
    fun `owner cannot create duplicate partner`() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val owner = client.registerAndLogin()

        client.post("/partners/create") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest("Unique Biz", ownerPin = "1234"))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        val secondResponse = client.post("/partners/create") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest("Duplicate Biz", ownerPin = "1234"))
        }

        assertEquals(HttpStatusCode.Conflict, secondResponse.status)
        val error = secondResponse.body<ApiMessage>()
        assertEquals(AppErrorCode.BUSINESS_ALREADY_EXISTS, error.code)
    }

    @Test
    fun `pin reset freezes account`() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val owner = client.registerAndLogin()

        client.post("/partners/create") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest("Freeze Biz", ownerPin = "1234"))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        val reset = client.post("/partners/pin/reset") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ResetPinRequest(confirm = true))
        }
        assertEquals(HttpStatusCode.OK, reset.status)

        val changeAttempt = client.put("/partners/pin") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(UpdatePinRequest(currentPin = null, newPin = "5678"))
        }

        assertEquals(HttpStatusCode.Forbidden, changeAttempt.status)
        val error = changeAttempt.body<ApiMessage>()
        assertEquals(AppErrorCode.ACCOUNT_FROZEN, error.code)
    }
}

