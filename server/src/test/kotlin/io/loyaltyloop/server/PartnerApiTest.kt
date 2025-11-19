package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.PartnerStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartnerApiTest {

    @Test
    fun testCreatePartnerFlow() = testApplication {
        configureTestEnv()
        val client = createJsonClient()

        // 1. Попытка создать бизнес БЕЗ авторизации (хакер)
        val unauthorizedRes = client.post("/partners/create") {
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest("Fake Business", "KG"))
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthorizedRes.status)

        // 2. Честная регистрация пользователя
        val authData = client.registerAndLogin() // Наш хелпер
        val token = authData.accessToken

        // 3. Создание бизнеса авторизованным юзером
        val createRes = client.post("/partners/create") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest("My Cool Coffee", "KG"))
        }
        
        assertEquals(HttpStatusCode.Created, createRes.status)
        
        // 4. Проверка БД (через репозиторий или API админа)
        // Для теста допустим, мы дернем ручку профиля и увидим там новую роль, 
        // либо проверим через Admin API (если бы оно было готово).
        // В Unit-тесте можно проверить напрямую через репозиторий, но тут Integration.
        
        // Проверим, что у юзера появилась роль OWNER в workspace
        val meRes = client.get("/client/me") {
            header("Authorization", "Bearer $token")
        }
        val profile = meRes.body<io.loyaltyloop.shared.models.UserProfileResponse>()
        
        val hasOwnerRole = profile.workspaces.any { 
            it.title == "My Cool Coffee" && it.role == io.loyaltyloop.shared.models.UserRole.PARTNER_ADMIN 
        }
        
        assertTrue(hasOwnerRole, "После создания бизнеса юзер должен стать его Владельцем")
    }
}