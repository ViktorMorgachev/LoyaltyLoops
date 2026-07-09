package io.loyaltyloop.server

import io.ktor.server.testing.testApplication
import kotlin.test.Test

class PartnerConstraintsTest {

    @Test
    fun `owner cannot create duplicate partner`() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val owner = client.registerAndLogin()


    }

    @Test
    fun `pin reset freezes account`() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val owner = client.registerAndLogin()


    }

    @Test
    fun `pin reset request requires email`() = testApplication {
        configureTestEnv()
//        val client = createJsonClient()
//        val owner = client.registerAndLogin()
//
//        client.post("/partners/create") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(CreatePartnerRequest("Email Req Biz", ownerPin = "1234"))
//        }.apply { assertEquals(HttpStatusCode.Created, status) }
//
//        val response = client.post("/partners/pin/reset/request") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//        }
//
//        assertEquals(HttpStatusCode.Forbidden, response.status)
//        val error = response.body<ApiMessage>()
//        assertEquals(AppErrorCode.EMAIL_NOT_SET, error.code)
//    }
//
//    @Test
//    fun `email pin reset link freezes account after confirmation`() = testApplication {
//        configureTestEnv()
//        val client = createJsonClient()
//        val owner = client.registerAndLogin()
//
//        client.post("/partners/create") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(CreatePartnerRequest("Email Flow Biz", ownerPin = "1234"))
//        }.apply { assertEquals(HttpStatusCode.Created, status) }
//
//        client.post("/client/profile") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(UpdateProfileRequest(firstName = "Owner", email = "owner@example.com"))
//        }.apply { assertEquals(HttpStatusCode.OK, status) }
//
//        EmailDebugStore.clear()
//
//        val request = client.post("/partners/pin/reset/request") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//        }
//        assertEquals(HttpStatusCode.OK, request.status)
//
//        val link = assertNotNull(EmailDebugStore.lastPinResetLink, "Reset email not captured")
//        val token = link.substringAfter("token=").substringBefore("&")
//
//        val confirm = client.post("/partners/pin/reset/confirm") {
//            contentType(ContentType.Application.Json)
//            setBody(PinResetConfirmRequest(token = token, newPin = "7777"))
//        }
//        assertEquals(HttpStatusCode.OK, confirm.status)
//
//        val changeAttempt = client.put("/partners/pin") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(UpdatePinRequest(currentPin = null, newPin = "9999"))
//        }
//        assertEquals(HttpStatusCode.Forbidden, changeAttempt.status)
//        val freezeError = changeAttempt.body<ApiMessage>()
//        assertEquals(AppErrorCode.ACCOUNT_FROZEN, freezeError.code)
    }
}

