package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserProfileResponse
import kotlin.test.*

class ClientTest {

    @Test
    fun test01_UpdateProfileSuccess() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val authResponse = client.registerAndLogin()
        val token = authResponse.accessToken

        // 2. Подготавливаем данные для обновления
        val updateData = UpdateProfileRequest(
            firstName = "Alex",
            lastName = "Tester",
            email = "alex@test.com",
        )

        // 3. Отправляем запрос на обновление
        val updateRes = client.post("/client/profile") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(updateData)
        }

        assertEquals(HttpStatusCode.OK, updateRes.status)

        // Проверяем, что вернулся корректный JSON ответ
        val apiMessage = updateRes.body<ApiMessage>()
        // Текст зависит от локали (по дефолту RU), но главное, что он не пустой
        assert(apiMessage.message.isNotEmpty())

        // 4. ПРОВЕРКА БАЗЫ: Запрашиваем профиль через /me
        val meRes = client.get("/auth/me") {
            header("Authorization", "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, meRes.status)
        val profile = meRes.body<UserProfileResponse>()

        // Сравниваем то, что отправляли, с тем, что сохранила база
        assertEquals("Alex", profile.firstName)
        assertEquals("Tester", profile.lastName)
        assertEquals("alex@test.com", profile.email)
    }
}