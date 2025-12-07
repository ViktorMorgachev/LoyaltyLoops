package io.loyaltyloop.server.i18n

object ServerResources {
    private val templates = mapOf(
        // Русский (Russian)
        "ru" to mapOf(
            "sub_expiring_subject" to "Истекает подписка",
            "sub_expiring_body" to "Внимание LoyaltyLoop: Подписка для точки \"{pointName}\" истекает {date}. Пожалуйста, продлите!",
            "sub_expiring_1day_subject" to "СРОЧНО LoyaltyLoop: Остался 1 день!",
            "sub_expiring_1day_body" to "Критическое предупреждение: Подписка для точки \"{pointName}\" истекает через 24 часа ({date}). Точка будет отключена!",
            // Manager specific
            "sub_expiring_manager_body" to "Внимание: Подписка для точки \"{pointName}\" (Партнер: {partnerName}) истекает {date}.",
            "sub_expiring_1day_manager_body" to "СРОЧНО: Подписка для точки \"{pointName}\" (Партнер: {partnerName}) истекает через 24 часа! Требуется продление."
        ),
        // English
        "en" to mapOf(
            "sub_expiring_subject" to "Subscription Expiring",
            "sub_expiring_body" to "LoyaltyLoop Alert: Subscription for point \"{pointName}\" expires on {date}. Please renew!",
            "sub_expiring_1day_subject" to "URGENT LoyaltyLoop: 1 Day Left!",
            "sub_expiring_1day_body" to "Critical Alert: Subscription for point \"{pointName}\" expires in 24 hours ({date}). The point will be deactivated!",
            // Manager specific
            "sub_expiring_manager_body" to "Attention: Subscription for point \"{pointName}\" (Partner: {partnerName}) expires on {date}.",
            "sub_expiring_1day_manager_body" to "URGENT: Subscription for point \"{pointName}\" (Partner: {partnerName}) expires in 24 hours! Renewal required."
        ),
        // Кыргызча (Kyrgyz)
        "ky" to mapOf(
            "sub_expiring_subject" to "Жазылуу мөөнөтү бүтүп баратат",
            "sub_expiring_body" to "LoyaltyLoop: \"{pointName}\" соода түйүнүнүн жазылуу мөөнөтү {date} күнү бүтөт. Сураныч, жазылууну узартыңыз!",
            "sub_expiring_1day_subject" to "ШАШЫЛЫШ LoyaltyLoop: 1 күн калды!",
            "sub_expiring_1day_body" to "Критикалык эскертүү: \"{pointName}\" үчүн жазылуу 24 сааттын ичинде бүтөт ({date})! Түйүн өчүрүлөт!",
            // Manager specific
            "sub_expiring_manager_body" to "Көңүл буруңуз: \"{pointName}\" (Өнөктөш: {partnerName}) үчүн жазылуу мөөнөтү {date} күнү бүтөт.",
            "sub_expiring_1day_manager_body" to "ШАШЫЛЫШ: \"{pointName}\" (Өнөктөш: {partnerName}) үчүн жазылуу мөөнөтү 24 сааттын ичинде бүтөт! Узартуу талап кылынат."
        ),
        "kg" to mapOf( // Alias for country code
            "sub_expiring_subject" to "Жазылуу мөөнөтү бүтүп баратат",
            "sub_expiring_body" to "LoyaltyLoop: \"{pointName}\" соода түйүнүнүн жазылуу мөөнөтү {date} күнү бүтөт. Сураныч, жазылууну узартыңыз!",
            "sub_expiring_1day_subject" to "ШАШЫЛЫШ LoyaltyLoop: 1 күн калды!",
            "sub_expiring_1day_body" to "Критикалык эскертүү: \"{pointName}\" үчүн жазылуу 24 сааттын ичинде бүтөт ({date})! Түйүн өчүрүлөт!",
            // Manager specific
            "sub_expiring_manager_body" to "Көңүл буруңуз: \"{pointName}\" (Өнөктөш: {partnerName}) үчүн жазылуу мөөнөтү {date} күнү бүтөт.",
            "sub_expiring_1day_manager_body" to "ШАШЫЛЫШ: \"{pointName}\" (Өнөктөш: {partnerName}) үчүн жазылуу мөөнөтү 24 сааттын ичинде бүтөт! Узартуу талап кылынат."
        ),
        // Қазақша (Kazakh)
        "kk" to mapOf(
            "sub_expiring_subject" to "Жазылым мерзімі аяқталуда",
            "sub_expiring_body" to "LoyaltyLoop: \"{pointName}\" сауда нүктесінің жазылымы {date} күні аяқталады. Жазылымды ұзартыңыз!",
            "sub_expiring_1day_subject" to "ШҰҒЫЛ LoyaltyLoop: 1 күн қалды!",
            "sub_expiring_1day_body" to "Маңызды ескерту: \"{pointName}\" үшін жазылым 24 сағат ішінде аяқталады ({date})! Нүкте өшіріледі!",
            // Manager specific
            "sub_expiring_manager_body" to "Назар аударыңыз: \"{pointName}\" (Серіктес: {partnerName}) үшін жазылым мерзімі {date} күні аяқталады.",
            "sub_expiring_1day_manager_body" to "ШҰҒЫЛ: \"{pointName}\" (Серіктес: {partnerName}) үшін жазылым мерзімі 24 сағат ішінде аяқталады! Ұзарту қажет."
        ),
        "kz" to mapOf( // Alias for country code
            "sub_expiring_subject" to "Жазылым мерзімі аяқталуда",
            "sub_expiring_body" to "LoyaltyLoop: \"{pointName}\" сауда нүктесінің жазылымы {date} күні аяқталады. Жазылымды ұзартыңыз!",
            "sub_expiring_1day_subject" to "ШҰҒЫЛ LoyaltyLoop: 1 күн қалды!",
            "sub_expiring_1day_body" to "Маңызды ескерту: \"{pointName}\" үшін жазылым 24 сағат ішінде аяқталады ({date})! Нүкте өшіріледі!",
            // Manager specific
            "sub_expiring_manager_body" to "Назар аударыңыз: \"{pointName}\" (Серіктес: {partnerName}) үшін жазылым мерзімі {date} күні аяқталады.",
            "sub_expiring_1day_manager_body" to "ШҰҒЫЛ: \"{pointName}\" (Серіктес: {partnerName}) үшін жазылым мерзімі 24 сағат ішінде аяқталады! Ұзарту қажет."
        ),
        // O'zbekcha (Uzbek)
        "uz" to mapOf(
            "sub_expiring_subject" to "Obuna muddati tugamoqda",
            "sub_expiring_body" to "LoyaltyLoop: \"{pointName}\" savdo nuqtasi uchun obuna muddati {date} da tugaydi. Iltimos, obunani uzaytiring!",
            "sub_expiring_1day_subject" to "SHOSHILINCH LoyaltyLoop: 1 kun qoldi!",
            "sub_expiring_1day_body" to "Muhim ogohlantirish: \"{pointName}\" uchun obuna 24 soat ichida tugaydi ({date})! Nuqta o'chiriladi!",
            // Manager specific
            "sub_expiring_manager_body" to "Diqqat: \"{pointName}\" (Hamkor: {partnerName}) uchun obuna {date} da tugaydi.",
            "sub_expiring_1day_manager_body" to "SHOSHILINCH: \"{pointName}\" (Hamkor: {partnerName}) uchun obuna 24 soat ichida tugaydi! Uzaytirish talab qilinadi."
        ),
        // Беларуская (Belarusian)
        "be" to mapOf(
            "sub_expiring_subject" to "Тэрмін падпіскі заканчваецца",
            "sub_expiring_body" to "Увага LoyaltyLoop: Тэрмін падпіскі для кропкі \"{pointName}\" заканчваецца {date}. Калі ласка, падоўжыце яе!",
            "sub_expiring_1day_subject" to "ТЭРМІНОВА LoyaltyLoop: Застаўся 1 дзень!",
            "sub_expiring_1day_body" to "Крытычнае папярэджанне: Падпіска для кропкі \"{pointName}\" заканчваецца праз 24 гадзіны ({date})! Кропка будзе адключана!",
            // Manager specific
            "sub_expiring_manager_body" to "Увага: Падпіска для кропкі \"{pointName}\" (Партнёр: {partnerName}) заканчваецца {date}.",
            "sub_expiring_1day_manager_body" to "ТЭРМІНОВА: Падпіска для кропкі \"{pointName}\" (Партнёр: {partnerName}) заканчваецца праз 24 гадзіны! Патрабуецца падаўжэнне."
        ),
        "by" to mapOf( // Alias for country code
            "sub_expiring_subject" to "Тэрмін падпіскі заканчваецца",
            "sub_expiring_body" to "Увага LoyaltyLoop: Тэрмін падпіскі для кропкі \"{pointName}\" заканчваецца {date}. Калі ласка, падоўжыце яе!",
            "sub_expiring_1day_subject" to "ТЭРМІНОВА LoyaltyLoop: Застаўся 1 дзень!",
            "sub_expiring_1day_body" to "Крытычнае папярэджанне: Падпіска для кропкі \"{pointName}\" заканчваецца праз 24 гадзіны ({date})! Кропка будзе адключана!",
            // Manager specific
            "sub_expiring_manager_body" to "Увага: Падпіска для кропкі \"{pointName}\" (Партнёр: {partnerName}) заканчваецца {date}.",
            "sub_expiring_1day_manager_body" to "ТЭРМІНОВА: Падпіска для кропкі \"{pointName}\" (Партнёр: {partnerName}) заканчваецца праз 24 гадзіны! Патрабуецца падаўжэнне."
        )
    )

    fun get(lang: String?, key: String, args: Map<String, String> = emptyMap()): String {
        val effectiveLang = lang?.lowercase() ?: "ru"
        
        // Priority: Requested Lang -> English -> Russian -> Key itself
        val template = templates[effectiveLang]?.get(key) 
            ?: templates["en"]?.get(key) 
            ?: templates["ru"]?.get(key)
            ?: return key

        var result = template
        args.forEach { (k, v) ->
            result = result.replace("{$k}", v)
        }
        return result
    }
}
