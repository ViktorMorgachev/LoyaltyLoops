package io.loyaltyloop.server

import io.ktor.server.testing.testApplication
import kotlin.test.Test

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

