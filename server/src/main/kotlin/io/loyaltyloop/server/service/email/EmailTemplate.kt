package io.loyaltyloop.server.service.email

import io.loyaltyloop.server.i18n.ServerResources
import io.loyaltyloop.shared.models.PlatformRequestType

sealed class EmailTemplate {

    /**
     * 🟢 **Приветствие Партнера**
     * Отправляется Владельцу сразу после регистрации бизнеса.
     * Содержит ссылку на вход в админку.
     */
    data class PartnerWelcome(val name: String, val loginUrl: String) : EmailTemplate()

    /**
     * 🟢 **Создание Точки**
     * Отправляется Владельцу/Менеджеру, когда они добавили новую торговую точку.
     * Напоминание о том, что нужно настроить график и купить подписку.
     */
    data class PointCreated(val pointName: String, val partnerName: String) : EmailTemplate()

    /**
     * 🔵 **Новая Заявка (Для Админов)**
     * Отправляется Супер-Админам платформы, когда Менеджер создает заявку
     * (на активацию точки, блокировку и т.д.). Требует реакции.
     */
    data class SubscriptionRequestCreated(
        val requestId: String,
        val type: PlatformRequestType,
        val partnerName: String,
        val amount: String,
        val requesterName: String
    ) : EmailTemplate()

    /**
     * 🟢/🔴 **Решение по Заявке**
     * Отправляется Менеджеру (автору заявки), когда Админ одобрил или отклонил её.
     */
    data class RequestDecision(
        val isApproved: Boolean,
        val type: PlatformRequestType,
        val pointName: String,
        val reason: String? = null
    ) : EmailTemplate()

    /**
     * 🔴 **Подписка Истекла (Факт)**
     * Отправляется Владельцу, когда точка уже отключилась (срок вышел).
     */
    data class SubscriptionExpired(val pointName: String, val date: String) : EmailTemplate()

    /**
     * ⚠️/🔥 **Предупреждение о Подписке**
     * Отправляется Владельцу и Менеджерам за 3 дня (Warning) и за 1 день (Urgent).
     * @param isManager - если true, текст адаптирован для сотрудника ("Свяжитесь с клиентом").
     */
    data class SubscriptionAlert(
        val isManager: Boolean,
        val pointName: String,
        val partnerName: String,
        val date: String,
        val isUrgent: Boolean
    ) : EmailTemplate()


    /**
     * 📊 **Сводный Отчет (Ежедневный)**
     * Отправляется Супер-Админам раз в сутки.
     * Содержит списки всех критических и истекающих подписок по всей платформе.
     */
    data class SuperAdminSummaryReport(
        val critical: List<SummaryItem>, // Осталось <= 1 день
        val warning: List<SummaryItem>   // Осталось <= 3 дня
    ) : EmailTemplate() {
        data class SummaryItem(
            val partner: String,
            val point: String,
            val date: String,
            val managerEmail: String? // Email менеджера, который ведет эту точку (чтобы пнуть его)
        )
    }
}

class EmailTemplateService {

    fun buildSubject(template: EmailTemplate, lang: String?): String {
        return when (template) {
            is EmailTemplate.PartnerWelcome -> ServerResources.get(lang, "welcome_subject")
            is EmailTemplate.PointCreated -> ServerResources.get(lang, "point_created_subject")

            // Системные уведомления (обычно на английском или дефолтном языке)
            is EmailTemplate.SubscriptionRequestCreated -> "Action Required: New Request (${template.type})"
            is EmailTemplate.SuperAdminSummaryReport -> "📊 Daily Subscription Health Check"

            is EmailTemplate.RequestDecision -> if (template.isApproved)
                ServerResources.get(lang, "request_approved_subject")
            else
                ServerResources.get(lang, "request_rejected_subject")

            is EmailTemplate.SubscriptionExpired -> ServerResources.get(lang, "sub_expired_subject")

            is EmailTemplate.SubscriptionAlert -> {
                val key = if (template.isUrgent) "sub_critical_subject" else "sub_expiring_subject"
                ServerResources.get(lang, key)
            }
        }
    }

    fun buildBody(template: EmailTemplate, lang: String?): String {
        val content = when (template) {
            // ... (Остальные кейсы остались без изменений, см. предыдущие ответы) ...
            is EmailTemplate.PartnerWelcome -> {
                val text = ServerResources.get(lang, "welcome_body", mapOf("partnerName" to template.name))
                val btn = ServerResources.get(lang, "welcome_btn")
                """
                <h2>$text</h2>
                <a href="${template.loginUrl}" class="button">$btn</a>
                """
            }

            // ... (PointCreated, RequestDecision, Expired, Alert - как раньше) ...
            // Для краткости я их скрыл, но они должны быть здесь.

            is EmailTemplate.SubscriptionRequestCreated -> """
                <h3>New Request Pending</h3>
                <table style="width:100%; text-align:left;">
                    <tr><td><b>Type:</b></td><td>${template.type}</td></tr>
                    <tr><td><b>Partner:</b></td><td>${template.partnerName}</td></tr>
                    <tr><td><b>Amount:</b></td><td>${template.amount}</td></tr>
                    <tr><td><b>By:</b></td><td>${template.requesterName}</td></tr>
                </table>
                <br>
                <a href="https://admin.loyaltyloop.io/requests/${template.requestId}" class="button">Open Admin Panel</a>
            """.trimIndent()

            // [NEW] Реализация отчета
            is EmailTemplate.SuperAdminSummaryReport -> buildSummaryReport(template)

            else -> "<p>Notification</p>" // Fallback
        }

        return wrapHtml(content)
    }

    // --- ГЕНЕРАТОР ОТЧЕТА ---
    private fun buildSummaryReport(data: EmailTemplate.SuperAdminSummaryReport): String {
        val sb = StringBuilder()
        sb.append("<h2>Daily Subscription Report</h2>")
        sb.append("<p>Here is the status of expiring subscriptions across the platform.</p>")

        // 1. CRITICAL SECTION (Red)
        if (data.critical.isNotEmpty()) {
            sb.append("<h3 style='color:#d32f2f; border-bottom: 2px solid #d32f2f; padding-bottom: 5px;'>🚨 CRITICAL (${data.critical.size})</h3>")
            sb.append("<p style='color:#666; font-size: 12px;'>Expiring in ≤ 24 hours. Automated SMS sent to owners.</p>")

            sb.append("<table style='width:100%; border-collapse: collapse; margin-bottom: 20px;'>")
            sb.append("<tr style='background-color:#ffebee; text-align:left;'><th style='padding:8px;'>Partner / Point</th><th style='padding:8px;'>Expires</th><th style='padding:8px;'>Manager</th></tr>")

            data.critical.forEach { item ->
                sb.append("<tr>")
                sb.append("<td style='padding:8px; border-bottom:1px solid #eee;'><b>${item.partner}</b><br><span style='color:#777; font-size:12px;'>${item.point}</span></td>")
                sb.append("<td style='padding:8px; border-bottom:1px solid #eee; color:#d32f2f;'><b>${item.date}</b></td>")
                sb.append("<td style='padding:8px; border-bottom:1px solid #eee;'>${item.managerEmail ?: "<span style='color:#ccc'>-</span>"}</td>")
                sb.append("</tr>")
            }
            sb.append("</table>")
        }

        // 2. WARNING SECTION (Orange)
        if (data.warning.isNotEmpty()) {
            sb.append("<h3 style='color:#f57c00; border-bottom: 2px solid #f57c00; padding-bottom: 5px;'>⚠️ WARNING (${data.warning.size})</h3>")
            sb.append("<p style='color:#666; font-size: 12px;'>Expiring in 3 days. Email notifications sent.</p>")

            sb.append("<table style='width:100%; border-collapse: collapse;'>")
            sb.append("<tr style='background-color:#fff3e0; text-align:left;'><th style='padding:8px;'>Partner / Point</th><th style='padding:8px;'>Expires</th><th style='padding:8px;'>Manager</th></tr>")

            data.warning.forEach { item ->
                sb.append("<tr>")
                sb.append("<td style='padding:8px; border-bottom:1px solid #eee;'>${item.partner}<br><span style='color:#777; font-size:12px;'>${item.point}</span></td>")
                sb.append("<td style='padding:8px; border-bottom:1px solid #eee;'>${item.date}</td>")
                sb.append("<td style='padding:8px; border-bottom:1px solid #eee;'>${item.managerEmail ?: "-"}</td>")
                sb.append("</tr>")
            }
            sb.append("</table>")
        }

        // 3. EMPTY STATE
        if (data.critical.isEmpty() && data.warning.isEmpty()) {
            sb.append("<div style='background-color:#e8f5e9; padding: 20px; border-radius: 8px; text-align: center;'>")
            sb.append("<h3 style='color:#2e7d32; margin:0;'>✅ All Systems Normal</h3>")
            sb.append("<p style='color:#4caf50; margin:5px 0 0;'>No subscriptions expiring in the next 3 days.</p>")
            sb.append("</div>")
        }

        return sb.toString()
    }

    private fun wrapHtml(content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #333; background-color: #f9fafb; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 20px auto; background-color: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .header { text-align: center; padding-bottom: 20px; border-bottom: 1px solid #eee; margin-bottom: 20px; }
                    .logo { font-size: 24px; font-weight: 800; color: #4F46E5; text-decoration: none; letter-spacing: -0.5px; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #4F46E5; color: white !important; text-decoration: none; border-radius: 6px; font-weight: bold; margin-top: 15px; }
                    .footer { font-size: 12px; color: #9ca3af; text-align: center; margin-top: 30px; border-top: 1px solid #eee; padding-top: 20px; }
                    table { font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <a href="https://loyaltyloop.io" class="logo">LoyaltyLoop</a>
                    </div>
                    $content
                    <div class="footer">
                        <p>© 2025 LoyaltyLoop Inc. • Automated System Message</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}