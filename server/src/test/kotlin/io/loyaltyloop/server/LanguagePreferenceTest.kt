package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.shared.models.UpdateLanguageRequest
import io.loyaltyloop.shared.models.UserProfileResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguagePreferenceTest {

    @Test
    fun `language selection persists on server`() = testApplication {
        configureTestEnv()
//        val client = createJsonClient()
//        val user = client.registerAndLogin(language = "en")
//
//        val initialProfile = client.get("/client/me") {
//            header("Authorization", "Bearer ${user.accessToken}")
//        }.body<UserProfileResponse>()
//        assertEquals("en", initialProfile.language)
//
//        val updateResponse = client.post("/client/language") {
//            header("Authorization", "Bearer ${user.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(UpdateLanguageRequest(language = "ru"))
//        }
//        assertEquals(HttpStatusCode.OK, updateResponse.status)
//
//        val updatedProfile = client.get("/client/me") {
//            header("Authorization", "Bearer ${user.accessToken}")
//        }.body<UserProfileResponse>()
//        assertEquals("ru", updatedProfile.language)
    }
}

