package io.loyaltyloop.server.i18n


object ServerResources {
    /**
     * Arguments used in templates:
     * {partnerName} - Название бизнеса
     * {pointName}   - Название точки
     * {date}        - Дата (форматированная)
     * {amount}      - Сумма (для заявок)
     * {type}        - Тип заявки
     * {requester}   - Имя менеджера
     * {reason}      - Причина отказа/блока
     */
    private val templates = mapOf(
        // =====================================================================
        // 🇷🇺 RUSSIAN (Русский)
        // =====================================================================
        "ru" to mapOf(
            // --- Welcome ---
            "welcome_subject" to "Добро пожаловать в LoyaltyLoops!",
            "welcome_body" to "Поздравляем! Ваш бизнес \"{partnerName}\" успешно зарегистрирован. Теперь вы можете добавлять торговые точки и запускать программу лояльности.",
            "welcome_btn" to "Перейти в Кабинет",

            // --- Point Created ---
            "point_created_subject" to "Новая торговая точка создана",
            "point_created_body" to "Точка \"{pointName}\" успешно добавлена. Не забудьте активировать подписку, чтобы начать работу.",

            // --- Request (To Admin) ---
            "request_new_subject" to "Новая заявка: {type}",
            "request_new_body" to "Поступила новая заявка от \"{partnerName}\".\nТип: {type}\nСумма: {amount}\nМенеджер: {requester}",

            // --- Request Decisions (To User) ---
            "request_approved_subject" to "Ваша заявка одобрена! ✅",
            "request_approved_body" to "Отличные новости! Ваша заявка ({type}) для \"{pointName}\" была одобрена. Сервисы активированы.",
            "request_rejected_subject" to "Заявка отклонена ❌",
            "request_rejected_body" to "Ваша заявка ({type}) была отклонена администратором.\nПричина: {reason}",

            // --- Subscriptions (Warnings) ---
            "sub_expiring_subject" to "Истекает подписка",
            "sub_expiring_body" to "Внимание: Подписка для точки \"{pointName}\" истекает {date}. Пожалуйста, продлите во избежание отключения.",
            "sub_expiring_manager_body" to "Напоминание: Подписка для \"{pointName}\" (Партнер: {partnerName}) заканчивается {date}.",

            // --- Subscriptions (Critical 1 Day) ---
            "sub_critical_subject" to "🔥 СРОЧНО: Осталось 24 часа!",
            "sub_critical_body" to "Критическое предупреждение! Точка \"{pointName}\" будет отключена завтра ({date}). Продлите подписку сейчас!",
            "sub_critical_manager_body" to "СРОЧНО: Подписка \"{pointName}\" (Партнер: {partnerName}) истекает завтра! Свяжитесь с владельцем.",

            // --- Subscriptions (Expired) ---
            "sub_expired_subject" to "❌ Точка отключена",
            "sub_expired_body" to "Срок действия подписки для \"{pointName}\" истек ({date}). Точка временно деактивирована.",

            // --- Support ---
            "support_reply_subject" to "Новое сообщение от Поддержки",
            "support_reply_body" to "Вам ответили в чате поддержки: \"{messageSnippet}...\"",

            // --- Pin Reset ---
            "pin_reset_subject" to "Сброс ПИН-кода",
            "pin_reset_body" to "Вы запросили сброс ПИН-кода. Нажмите на ссылку ниже, чтобы задать новый ПИН:\n{resetLink}\nЕсли вы этого не делали, проигнорируйте это письмо.",
            "pin_reset_partner_subject" to "Сброс ПИН-кода для Партнера",
            "pin_reset_partner_body" to "Вы запросили сброс ПИН-кода для вашего партнерского аккаунта. Нажмите на ссылку ниже, чтобы задать новый ПИН:\n{resetLink}\nЕсли вы этого не делали, проигнорируйте это письмо."
        ),

        // =====================================================================
        // 🇺🇸 ENGLISH (International)
        // =====================================================================
        "en" to mapOf(
            "welcome_subject" to "Welcome to LoyaltyLoop!",
            "welcome_body" to "Congratulations! Your business \"{partnerName}\" has been registered. You can now add trading points and launch your loyalty program.",
            "welcome_btn" to "Go to Dashboard",

            "point_created_subject" to "New Trading Point Created",
            "point_created_body" to "Point \"{pointName}\" has been added. Don't forget to activate a subscription to start working.",

            "request_new_subject" to "New Request: {type}",
            "request_new_body" to "New request received from \"{partnerName}\".\nType: {type}\nAmount: {amount}\nRequester: {requester}",

            "request_approved_subject" to "Request Approved! ✅",
            "request_approved_body" to "Great news! Your request ({type}) for \"{pointName}\" has been approved. Services are active.",
            "request_rejected_subject" to "Request Rejected ❌",
            "request_rejected_body" to "Your request ({type}) was rejected by the administrator.\nReason: {reason}",

            "sub_expiring_subject" to "Subscription Expiring",
            "sub_expiring_body" to "Attention: Subscription for \"{pointName}\" expires on {date}. Please renew to avoid interruption.",
            "sub_expiring_manager_body" to "Reminder: Subscription for \"{pointName}\" (Partner: {partnerName}) ends on {date}.",

            "sub_critical_subject" to "🔥 URGENT: 24 Hours Left!",
            "sub_critical_body" to "Critical Alert! Point \"{pointName}\" will be deactivated tomorrow ({date}). Renew now!",
            "sub_critical_manager_body" to "URGENT: Subscription for \"{pointName}\" (Partner: {partnerName}) expires tomorrow! Contact the owner.",

            "sub_expired_subject" to "❌ Point Deactivated",
            "sub_expired_body" to "Subscription for \"{pointName}\" has expired on {date}. The point is temporarily inactive.",

            "support_reply_subject" to "New Message from Support",
            "support_reply_body" to "You have a new reply in support chat: \"{messageSnippet}...\"",

            // --- Pin Reset ---
            "pin_reset_subject" to "PIN Reset",
            "pin_reset_body" to "You have requested a PIN reset. Click the link below to set a new PIN:\n{resetLink}\nIf you did not request this, please ignore this email.",
            "pin_reset_partner_subject" to "Partner PIN Reset",
            "pin_reset_partner_body" to "You have requested a PIN reset for your partner account. Click the link below to set a new PIN:\n{resetLink}\nIf you did not request this, please ignore this email."
        ),

        // =====================================================================
        // 🇰🇬 KYRGYZ (Кыргызча)
        // =====================================================================
        "ky" to mapOf(
            "welcome_subject" to "LoyaltyLoop'ко кош келиңиз!",
            "welcome_body" to "Куттуктайбыз! Сиздин \"{partnerName}\" бизнесиңиз ийгиликтүү катталды. Эми соода түйүндөрүн кошуп, лоялдуулук программасын баштасаңыз болот.",
            "welcome_btn" to "Кабинетке кирүү",

            "point_created_subject" to "Жаңы соода түйүнү түзүлдү",
            "point_created_body" to "\"{pointName}\" түйүнү кошулду. Ишти баштоо үчүн жазылууну активдештирүүнү унутпаңыз.",

            "request_approved_subject" to "Өтүнмөңүз кабыл алынды! ✅",
            "request_approved_body" to "Жакшы жаңылык! \"{pointName}\" үчүн өтүнмөңүз ({type}) жактырылды. Кызматтар иштеп баштады.",
            "request_rejected_subject" to "Өтүнмө четке кагылды ❌",
            "request_rejected_body" to "Сиздин өтүнмөңүз ({type}) администратор тарабынан четке кагылды.\nСебеби: {reason}",

            "sub_expiring_subject" to "Жазылуу мөөнөтү бүтүп баратат",
            "sub_expiring_body" to "Көңүл буруңуз: \"{pointName}\" үчүн жазылуу {date} күнү бүтөт. Үзгүлтүккө учурабаш үчүн узартыңыз.",
            "sub_expiring_manager_body" to "Эскертүү: \"{pointName}\" (Өнөктөш: {partnerName}) жазылуусу {date} күнү аяктайт.",

            "sub_critical_subject" to "🔥 ШАШЫЛЫШ: 24 саат калды!",
            "sub_critical_body" to "Критикалык билдирүү! \"{pointName}\" түйүнү эртең ({date}) өчүрүлөт. Тез арада узартыңыз!",
            "sub_critical_manager_body" to "ШАШЫЛЫШ: \"{pointName}\" (Өнөктөш: {partnerName}) жазылуусу эртең бүтөт! Ээси менен байланышыңыз.",

            "sub_expired_subject" to "❌ Түйүн өчүрүлдү",
            "sub_expired_body" to "\"{pointName}\" үчүн жазылуу мөөнөтү бүттү ({date}). Түйүн убактылуу токтотулду.",

            "support_reply_subject" to "Колдоо кызматынан жаңы билдирүү",
            "support_reply_body" to "Сизге жооп келди: \"{messageSnippet}...\"",

            // --- Pin Reset ---
            "pin_reset_subject" to "ПИН-кодду алмаштыруу",
            "pin_reset_body" to "Сиз ПИН-кодду алмаштырууну сурандыңыз. Жаңы ПИН коюу үчүн төмөнкү шилтемени басыңыз:\n{resetLink}\nЭгер сиз муну кылбасаңыз, бул катты этибарга албаңыз.",
            "pin_reset_partner_subject" to "Өнөктөштүн ПИН-кодун алмаштыруу",
            "pin_reset_partner_body" to "Сиз өнөктөштүк аккаунтуңуз үчүн ПИН-кодду алмаштырууну сурандыңыз. Жаңы ПИН коюу үчүн төмөнкү шилтемени басыңыз:\n{resetLink}\nЭгер сиз муну кылбасаңыз, бул катты этибарга албаңыз."
        ),

        // =====================================================================
        // 🇰🇿 KAZAKH (Қазақша)
        // =====================================================================
        "kk" to mapOf(
            "welcome_subject" to "LoyaltyLoop-қа қош келдіңіз!",
            "welcome_body" to "Құттықтаймыз! \"{partnerName}\" бизнесіңіз сәтті тіркелді. Енді сауда нүктелерін қосып, адалдық бағдарламасын іске қоса аласыз.",
            "welcome_btn" to "Кабинетке өту",

            "point_created_subject" to "Жаңа сауда нүктесі құрылды",
            "point_created_body" to "\"{pointName}\" нүктесі қосылды. Жұмысты бастау үшін жазылымды белсендіруді ұмытпаңыз.",

            "request_approved_subject" to "Өтінім мақұлданды! ✅",
            "request_approved_body" to "Керемет жаңалық! \"{pointName}\" үшін өтініміңіз ({type}) мақұлданды. Қызметтер қосылды.",
            "request_rejected_subject" to "Өтінім қабылданбады ❌",
            "request_rejected_body" to "Сіздің өтініміңіз ({type}) әкімші тарапынан қабылданбады.\nСебебі: {reason}",

            "sub_expiring_subject" to "Жазылым мерзімі аяқталуда",
            "sub_expiring_body" to "Назар аударыңыз: \"{pointName}\" үшін жазылым {date} күні аяқталады. Өшіп қалмас үшін ұзартыңыз.",
            "sub_expiring_manager_body" to "Ескерту: \"{pointName}\" (Серіктес: {partnerName}) жазылымы {date} күні бітеді.",

            "sub_critical_subject" to "🔥 ШҰҒЫЛ: 24 сағат қалды!",
            "sub_critical_body" to "Маңызды хабарлама! \"{pointName}\" нүктесі ертең ({date}) өшіріледі. Қазір ұзартыңыз!",
            "sub_critical_manager_body" to "ШҰҒЫЛ: \"{pointName}\" (Серіктес: {partnerName}) жазылымы ертең бітеді! Иесімен хабарласыңыз.",

            "sub_expired_subject" to "❌ Нүкте өшірілді",
            "sub_expired_body" to "\"{pointName}\" үшін жазылым мерзімі аяқталды ({date}). Нүкте уақытша тоқтатылды.",

            "support_reply_subject" to "Қолдау қызметінен жаңа хабарлама",
            "support_reply_body" to "Сізге жауап келді: \"{messageSnippet}...\"",

            // --- Pin Reset ---
            "pin_reset_subject" to "ПИН-кодты қалпына келтіру",
            "pin_reset_body" to "Сіз ПИН-кодты қалпына келтіруді сұрадыңыз. Жаңа ПИН орнату үшін төмендегі сілтемені басыңыз:\n{resetLink}\nЕгер бұны сіз сұрамасаңыз, бұл хатты елемеңіз.",
            "pin_reset_partner_subject" to "Серіктестің ПИН-кодын қалпына келтіру",
            "pin_reset_partner_body" to "Сіз серіктестік аккаунтыңыз үшін ПИН-кодты қалпына келтіруді сұрадыңыз. Жаңа ПИН орнату үшін төмендегі сілтемені басыңыз:\n{resetLink}\nЕгер бұны сіз сұрамасаңыз, бұл хатты елемеңіз."
        ),

        // =====================================================================
        // 🇺🇿 UZBEK (O'zbekcha)
        // =====================================================================
        "uz" to mapOf(
            "welcome_subject" to "LoyaltyLoop-ga xush kelibsiz!",
            "welcome_body" to "Tabriklaymiz! \"{partnerName}\" biznesingiz muvaffaqiyatli ro'yxatdan o'tdi. Endi savdo nuqtalarini qo'shib, sodiqlik dasturini ishga tushirishingiz mumkin.",
            "welcome_btn" to "Kabinetga o'tish",

            "point_created_subject" to "Yangi savdo nuqtasi yaratildi",
            "point_created_body" to "\"{pointName}\" nuqtasi qo'shildi. Ishni boshlash uchun obunani faollashtirishni unutmang.",

            "request_approved_subject" to "Arizangiz tasdiqlandi! ✅",
            "request_approved_body" to "Ajoyib yangilik! \"{pointName}\" uchun arizangiz ({type}) tasdiqlandi. Xizmatlar faollashdi.",
            "request_rejected_subject" to "Ariza rad etildi ❌",
            "request_rejected_body" to "Sizning arizangiz ({type}) administrator tomonidan rad etildi.\nSababi: {reason}",

            "sub_expiring_subject" to "Obuna muddati tugamoqda",
            "sub_expiring_body" to "Diqqat: \"{pointName}\" uchun obuna {date} da tugaydi. Uzilish bo'lmasligi uchun uzaytiring.",
            "sub_expiring_manager_body" to "Eslatma: \"{pointName}\" (Hamkor: {partnerName}) obunasi {date} da tugaydi.",

            "sub_critical_subject" to "🔥 SHOSHILINCH: 24 soat qoldi!",
            "sub_critical_body" to "Muhim ogohlantirish! \"{pointName}\" nuqtasi ertaga ({date}) o'chiriladi. Hozir uzaytiring!",
            "sub_critical_manager_body" to "SHOSHILINCH: \"{pointName}\" (Hamkor: {partnerName}) obunasi ertaga tugaydi! Egasi bilan bog'laning.",

            "sub_expired_subject" to "❌ Nuqta o'chirildi",
            "sub_expired_body" to "\"{pointName}\" uchun obuna muddati tugadi ({date}). Nuqta vaqtincha faol emas.",

            "support_reply_subject" to "Qo'llab-quvvatlash xizmatidan yangi xabar",
            "support_reply_body" to "Sizga javob keldi: \"{messageSnippet}...\"",

            // --- Pin Reset ---
            "pin_reset_subject" to "PIN-kodni tiklash",
            "pin_reset_body" to "Siz PIN-kodni tiklashni so'radingiz. Yangi PIN o'rnatish uchun quyidagi havolani bosing:\n{resetLink}\nAgar siz buni so'ramagan bo'lsangiz, ushbu xatni e'tiborsiz qoldiring.",
            "pin_reset_partner_subject" to "Hamkor uchun PIN-kodni tiklash",
            "pin_reset_partner_body" to "Siz hamkorlik hisobingiz uchun PIN-kodni tiklashni so'radingiz. Yangi PIN o'rnatish uchun quyidagi havolani bosing:\n{resetLink}\nAgar siz buni so'ramagan bo'lsangiz, ushbu xatni e'tiborsiz qoldiring."
        ),

        // =====================================================================
        // 🇧🇾 BELARUSIAN (Беларуская)
        // =====================================================================
        "be" to mapOf(
            "welcome_subject" to "Сардэчна запрашаем у LoyaltyLoop!",
            "welcome_body" to "Віншуем! Ваш бізнес \"{partnerName}\" паспяхова зарэгістраваны. Цяпер вы можаце дадаваць гандлёвыя кропкі і запускаць праграму лаяльнасці.",
            "welcome_btn" to "Перайсці ў Кабінет",

            "point_created_subject" to "Новая гандлёвая кропка створана",
            "point_created_body" to "Кропка \"{pointName}\" паспяхова дададзена. Не забудзьцеся актываваць падпіску, каб пачаць працу.",

            "request_approved_subject" to "Ваша заяўка ўхвалена! ✅",
            "request_approved_body" to "Выдатныя навіны! Ваша заяўка ({type}) для \"{pointName}\" была ўхвалена. Сэрвісы актываваны.",
            "request_rejected_subject" to "Заяўка адхілена ❌",
            "request_rejected_body" to "Ваша заяўка ({type}) была адхілена адміністратарам.\nПрычына: {reason}",

            "sub_expiring_subject" to "Тэрмін падпіскі заканчваецца",
            "sub_expiring_body" to "Увага: Падпіска для кропкі \"{pointName}\" заканчваецца {date}. Калі ласка, падоўжыце, каб пазбегнуць адключэння.",
            "sub_expiring_manager_body" to "Напамін: Падпіска для \"{pointName}\" (Партнёр: {partnerName}) заканчваецца {date}.",

            "sub_critical_subject" to "🔥 ТЭРМІНОВА: Засталося 24 гадзіны!",
            "sub_critical_body" to "Крытычнае папярэджанне! Кропка \"{pointName}\" будзе адключана заўтра ({date}). Падоўжыце падпіску зараз!",
            "sub_critical_manager_body" to "ТЭРМІНОВА: Падпіска \"{pointName}\" (Партнёр: {partnerName}) заканчваецца заўтра! Звяжыцеся з уладальнікам.",

            "sub_expired_subject" to "❌ Кропка адключана",
            "sub_expired_body" to "Тэрмін дзеяння падпіскі для \"{pointName}\" скончыўся ({date}). Кропка часова неактыўная.",

            "support_reply_subject" to "Новае паведамленне ад Падтрымкі",
            "support_reply_body" to "Вам адказалі ў чаце падтрымкі: \"{messageSnippet}...\"",

            // --- Pin Reset ---
            "pin_reset_subject" to "Скід ПІН-кода",
            "pin_reset_body" to "Вы запрасілі скід ПІН-кода. Націсніце на спасылку ніжэй, каб задаць новы ПІН:\n{resetLink}\nКалі вы гэтага не рабілі, праігнаруйце гэты ліст.",
            "pin_reset_partner_subject" to "Скід ПІН-кода для Партнёра",
            "pin_reset_partner_body" to "Вы запрасілі скід ПІН-кода для вашага партнёрскага акаўнта. Націсніце на спасылку ніжэй, каб задаць новы ПІН:\n{resetLink}\nКалі вы гэтага не рабілі, праігнаруйце гэты ліст."
        )
    )

    // Alias mappings for country codes
    init {
        (templates as MutableMap)["kg"] = templates["ky"]!!
        templates["kz"] = templates["kk"]!!
        templates["by"] = templates["be"]!!
    }

    fun get(lang: String?, key: String, args: Map<String, String> = emptyMap()): String {
        val effectiveLang = lang?.lowercase()?.take(2) ?: "ru"

        // Priority: Lang -> English -> Russian -> Key
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
