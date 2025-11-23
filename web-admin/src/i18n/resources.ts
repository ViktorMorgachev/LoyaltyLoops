export const resources = {
  // --- РУССКИЙ (Russian) ---
  ru: {
    translation: {
      common: {
        loading: "Загрузка...",
        error: "Ошибка",
        save: "Сохранить",
        cancel: "Отмена",
        create: "Создать",
        add: "Добавить",
        status: "Статус",
        actions: "Действия",
        active: "Активен",
        blocked: "Заблокирован",
        pending: "На проверке",
        copied: "Скопировано",
        yes: "Да",
        no: "Нет",
        approve: "Одобрить",
        block: "Блок",
        unblock: "Разблок",
        status_active: "Активен",
        status_not_paid: "Не оплачена",
        week: "Неделя",
        month: "Месяц",
        months: "Месяцев",
        year: "Год",
        revenue_chart: "Динамика Выручки"
      },
      errors: {
        INVALID_PHONE: "Неверный формат номера",
        INVALID_CODE: "Неверный код",
        CODE_EXPIRED: "Код истек",
        USER_NOT_FOUND: "Пользователь не найден",
        USER_CREATION_FAILED: "Ошибка создания пользователя",
        TOKEN_EXPIRED: "Сессия истекла",
        TOKEN_INVALID: "Ошибка сессии (Токен невалиден)",
        INVALID_PIN: "Неверный PIN",
        FORBIDDEN: "Доступ запрещен",
        BUSINESS_ALREADY_EXISTS: "У вас уже есть бизнес",
        BUSINESS_NOT_FOUND: "Бизнес не найден",
        POINT_NOT_FOUND: "Точка не найдена",
        UNKNOWN_ERROR: "Неизвестная ошибка",
        INTERNAL_ERROR: "Внутренняя ошибка",
        INVALID_REQUEST: "Ошибка запроса",
        UNAUTHORIZED: "Требуется авторизация",
        NOT_FOUND: "Ресурс не найден",
        POINT_INACTIVE: "Торговая точка не активна",
        QR_EXPIRED: "Срок действия QR истек",
        INVALID_QR_SIGNATURE: "Поддельный QR код",
        INVALID_AMOUNT: "Некорректная сумма операции",
        SUCCESS: "Успешно",
        INVALID_INVITE_CODE: "Неверный инвайт код",
        ALREADY_JOINED: "Вы уже присоединены",
        SECURITY_QR_SECRET_MISSING: "Критическая ошибка безопасности (QR Secret)",
        LOYALTY_SETTING_NOT_FOUND: "Настройки лояльности не найдены",
        CARD_NOT_FOUND: "Карта лояльности не найдена",
      },
      auth: {
        title: "Вход в панель управления",
        phone_label: "Номер телефона",
        code_label: "Код из СМС",
        get_code: "Получить код",
        login_btn: "Войти",
        change_number: "Изменить номер",
        code_sent: "Код отправлен (Смотри логи сервера)",
        success: "Успешный вход!"
      },
      menu: {
        title: "Консоль",
        
        // Общее
        dashboard: "Главная",
        switch_role: "Сменить роль",
        profile: "Профиль",
        logout: "Выйти",

        // Партнер
        my_points: "Торговые точки",
        my_clients: "Клиенты",
        transactions: "История",
        settings: "Настройки",
        staff: "Сотрудники",

        // Админ
        admin_dashboard: "Обзор",
        admin_partners: "Партнеры",
        
        // Новичок
        create_business: "Создать Бизнес"
      },
      // --- ИСТОРИЯ ---
      history: {
        title: "История Транзакций",
        table_date: "Дата",
        table_point: "Точка",
        table_type: "Тип",
        table_amount: "Сумма",
        table_bonus: "Бонусы",
        empty: "Транзакций пока нет",
        type_visit: "Визит (+1)",
        type_earn: "Покупка (Начисление)",
        type_spend: "Списание"
      },
      // --- СОТРУДНИКИ (NEW) ---
      staff: {
        title: "Управление Персоналом",
        managers: "Менеджеры",
        cashiers: "Кассиры",
        invite_manager: "Пригласить менеджера",
        invite_hint: "Отправьте этот код сотруднику, чтобы он присоединился как Менеджер:",
        generate_btn: "Сгенерировать код",
        role: "Роль",
        name: "Имя",
        phone: "Телефон",
        active: "Активен",
        fire: "Уволить",
        empty_managers: "Менеджеров пока нет",
        empty_cashiers: "Кассиров пока нет",
        confirm_fire: "Вы уверены, что хотите уволить этого сотрудника?",
        multi_points: "{{count}} Точки",
        role_manager: "Менеджер",
        role_cashier: "Кассир"
      },
      // --- НАСТРОЙКИ БИЗНЕСА ---
      settings: {
        title: "Настройки Бизнеса",
        name_label: "Название бренда",
        color_label: "Фирменный цвет",
        color_helper: "Этот цвет будет отображаться на карте клиента",
        logo_label: "Ссылка на логотип",
        save_success: "Настройки успешно сохранены",
        expiration_policy: "Политика сгорания (для всех точек)",
        expiration_hint: "Правила сгорания настраиваются здесь и действуют на всю сеть."
      },
      dashboard: {
        title: "Мои торговые точки",
        add_point: "Добавить филиал",
        empty: "Нет точек. Создайте первую!",
        table_name: "Название",
        table_type: "Тип",
        table_invite: "Инвайт для Кассира",
        create_title: "Новый филиал",
        create_business_title: "Запустите свою систему лояльности",
        create_business_subtitle: "У вас пока нет зарегистрированного бизнеса. Создайте его.",
        create_business_btn: "Создать Бизнес",
        modal_biz_title: "Регистрация Компании",
        modal_biz_name: "Название бизнеса",

        // Поля формы создания точки
        label_point_name: "Название филиала",
        label_point_type: "Тип заведения",
        label_strategy: "Стратегия Лояльности",
        label_target: "Цель (сколько собрать?)",
        label_cashback: "Начальный Кешбэк (%)",

        // --- НОВЫЕ КЛЮЧИ ДЛЯ УРОВНЕЙ ---
        label_lvl_start: "Старт (%)",
        label_lvl_silver: "Серебро (%)",
        label_lvl_gold: "Золото (%)",

        hint_target: "Например: 6 (каждый 6-й бесплатно)",
        hint_cashback: "Например: 5 (начнем с 5%)",
        hint_tiered_levels: "Будут созданы 3 уровня: Старт ({{base}}%), Серебро ({{mid}}%), Золото ({{max}}%)",

        types: {
            COFFEE_SHOP: "Кофейня",
            RESTAURANT: "Ресторан",
            RETAIL: "Магазин",
            SERVICE: "Услуги",
            OTHER: "Другое"
        },
        strategies: {
            TIERED_LTV: "Накопительная (Кешбэк)",
            VISIT_COUNTER: "Счетчик (N-й в подарок)",
            HYBRID: "Смешанная (Баллы + Визиты)"
        }
      },
      profile: {
        title: "Мой Профиль",
        id_label: "ID Пользователя (нажмите для копирования)",
        phone_label: "Телефон",
        name_label: "Имя",
        save_btn: "Сохранить изменения",
        my_workspaces: "Мои рабочие области",
        current_role: "Текущая",
        switch_role: "Перейти",
        no_workspaces: "Рабочие области не найдены",
        pin_protected: "Защищено PIN",
        switched_success: "Переключено на {{name}}"
      },
      point_details: {
        title: "Управление Филиалом",
        tab_overview: "Обзор",
        tab_settings: "Настройки",
        tab_staff: "Персонал",
        burn_bonuses: "Срок жизни бонусов (дней)",
        downgrade_tier: "Срок до сброса уровня (дней)",
        days: "дн.",
        delete_point: "Удалить точку",
        fire_cashier: "Уволить",
        confirm_delete: "Вы уверены? Это действие необратимо.",
        invite_code: "Код для кассира",
        save_settings: "Сохранить настройки",
        staff_empty: "Кассиров пока нет",
        levels_config: "Настройка Уровней",
        lvl_name: "Название",
        lvl_threshold: "Порог (сумма)",
        lvl_percent: "Кешбэк (%)",
        address_label: "Адрес (введите вручную)",
        max_burn_label: "Макс. % оплаты баллами",
        max_burn_hint: "Сколько процентов от чека можно покрыть бонусами (0-100)",
        map_location: "Местоположение на карте"
      },
      admin: {
        title: "Управление Партнерами",
        table_owner: "Владелец (Тел)",
        table_country: "Страна",
        details_title: "Детали Партнера",
        total_points: "Всего Точек",
        total_clients: "Всего Клиентов",
        total_transactions: "Всего Транзакций",
        total_revenue: "Общая Выручка",
        trading_points: "Торговые Точки",
        no_points: "Точек нет",
        id: "ID"
      }
    }
  },

  // --- АНГЛИЙСКИЙ (English) ---
  en: {
    translation: {
      common: {
        loading: "Loading...",
        error: "Error",
        save: "Save",
        cancel: "Cancel",
        create: "Create",
        add: "Add",
        status: "Status",
        actions: "Actions",
        active: "Active",
        blocked: "Blocked",
        pending: "Pending",
        copied: "Copied",
        yes: "Yes",
        no: "No",
        approve: "Approve",
        block: "Block",
        unblock: "Unblock",
        status_active: "Active",
        status_not_paid: "Not Paid",
        week: "Week",
        month: "Month",
        months: "Months",
        year: "Year",
        revenue_chart: "Revenue Dynamics"
      },
      errors: {
        INVALID_PHONE: "Invalid phone number",
        INVALID_CODE: "Invalid code",
        CODE_EXPIRED: "Code expired",
        USER_NOT_FOUND: "User not found",
        USER_CREATION_FAILED: "User creation failed",
        TOKEN_EXPIRED: "Session expired",
        TOKEN_INVALID: "Session error (Token invalid)",
        INVALID_PIN: "Invalid PIN",
        FORBIDDEN: "Access denied",
        BUSINESS_ALREADY_EXISTS: "You already have a business",
        BUSINESS_NOT_FOUND: "Business not found",
        POINT_NOT_FOUND: "Point not found",
        UNKNOWN_ERROR: "Unknown error",
        INTERNAL_ERROR: "Internal Error",
        INVALID_REQUEST: "Invalid request",
        UNAUTHORIZED: "Unauthorized",
        NOT_FOUND: "Resource not found",
        POINT_INACTIVE: "Trading point is inactive",
        QR_EXPIRED: "QR code expired",
        INVALID_QR_SIGNATURE: "Fake QR code",
        INVALID_AMOUNT: "Invalid amount",
        SUCCESS: "Success",
        INVALID_INVITE_CODE: "Invalid invite code",
        ALREADY_JOINED: "Already joined",
        SECURITY_QR_SECRET_MISSING: "Critical Security Error (QR Secret)",
        LOYALTY_SETTING_NOT_FOUND: "Loyalty settings not found",
        CARD_NOT_FOUND: "Loyalty card not found",
      },
      auth: {
        title: "Admin Panel Login",
        phone_label: "Phone Number",
        code_label: "SMS Code",
        get_code: "Get Code",
        login_btn: "Log In",
        change_number: "Change Number",
        code_sent: "Code sent",
        success: "Login Success!"
      },
      menu: {
        title: "Console",
        
        // Common
        dashboard: "Dashboard",
        switch_role: "Switch Role",
        profile: "Profile",
        logout: "Logout",

        // Partner
        my_points: "Trading Points",
        my_clients: "Clients",
        transactions: "History",
        settings: "Settings",
        staff: "Staff",

        // Admin
        admin_dashboard: "Overview",
        admin_partners: "Partners",
        
        // New User
        create_business: "Create Business"
      },
      history: {
        title: "Transaction History",
        table_date: "Date",
        table_point: "Point",
        table_type: "Type",
        table_amount: "Amount",
        table_bonus: "Bonus",
        empty: "No transactions yet",
        type_visit: "Visit (+1)",
        type_earn: "Purchase (Earn)",
        type_spend: "Redeem"
      },
      settings: {
        title: "Business Settings",
        name_label: "Brand Name",
        color_label: "Brand Color",
        color_helper: "This color will be displayed on the client card",
        logo_label: "Logo URL",
        save_success: "Settings saved",
        expiration_policy: "Expiration Policy (Global)",
        expiration_hint: "Expiration rules are configured here and apply to all branches."
      },
      staff: {
        title: "Staff Management",
        managers: "Managers",
        cashiers: "Cashiers",
        invite_manager: "Invite Manager",
        invite_hint: "Send this code to the employee to join as a Manager:",
        generate_btn: "Generate Code",
        role: "Role",
        name: "Name",
        phone: "Phone",
        active: "Active",
        fire: "Fire",
        empty_managers: "No managers yet",
        empty_cashiers: "No cashiers yet",
        confirm_fire: "Are you sure you want to fire this employee?",
        multi_points: "{{count}} Points",
        role_manager: "Manager",
        role_cashier: "Cashier"
      },
      dashboard: {
        title: "My Trading Points",
        add_point: "Add Branch",
        empty: "No points yet. Create the first one!",
        table_name: "Name",
        table_type: "Type",
        table_invite: "Cashier Invite",
        create_title: "New Branch",
        create_business_title: "Launch your Loyalty System",
        create_business_subtitle: "You don't have a registered business yet. Create one.",
        create_business_btn: "Create Business",
        modal_biz_title: "Company Registration",
        modal_biz_name: "Business Name",

        label_point_name: "Branch Name",
        label_point_type: "Venue Type",
        label_strategy: "Loyalty Strategy",
        label_target: "Target (how many?)",
        label_cashback: "Base Cashback (%)",

        label_lvl_start: "Start (%)",
        label_lvl_silver: "Silver (%)",
        label_lvl_gold: "Gold (%)",

        hint_target: "E.g.: 6 (every 6th is free)",
        hint_cashback: "E.g.: 5 (start with 5%)",
        hint_tiered_levels: "3 Levels will be created: Start ({{base}}%), Silver ({{mid}}%), Gold ({{max}}%)",

        types: {
            COFFEE_SHOP: "Coffee Shop",
            RESTAURANT: "Restaurant",
            RETAIL: "Retail",
            SERVICE: "Service",
            OTHER: "Other"
        },
        strategies: {
            TIERED_LTV: "Cashback (Tiered)",
            VISIT_COUNTER: "Visits (Nth free)",
            HYBRID: "Hybrid (Points + Visits)"
        }
      },
      profile: {
        title: "My Profile",
        id_label: "User ID (Click to copy)",
        phone_label: "Phone",
        name_label: "Name",
        save_btn: "Save Changes",
        my_workspaces: "My Workspaces",
        current_role: "Current",
        switch_role: "Switch",
        no_workspaces: "No workspaces found",
        pin_protected: "PIN protected",
        switched_success: "Switched to {{name}}"
      },
      point_details: {
        title: "Branch Management",
        tab_overview: "Overview",
        tab_settings: "Settings",
        tab_staff: "Staff",
        burn_bonuses: "Points Expiration (days)",
        downgrade_tier: "Tier Reset (days)",
        days: "days",
        delete_point: "Delete Branch",
        fire_cashier: "Fire",
        confirm_delete: "Are you sure? This action is irreversible.",
        invite_code: "Cashier Invite Code",
        save_settings: "Save Settings",
        staff_empty: "No cashiers yet",
        address_label: "Address (manual entry)",
        max_burn_label: "Max Points Payment %",
        max_burn_hint: "What percentage of the bill can be paid with points (0-100)",
        map_location: "Map Location"
      },
      admin: {
        title: "Partner Management",
        table_owner: "Owner (Phone)",
        table_country: "Country",
        details_title: "Partner Details",
        total_points: "Total Points",
        total_clients: "Total Clients",
        total_transactions: "Total Transactions",
        total_revenue: "Total Revenue",
        trading_points: "Trading Points",
        no_points: "No points found",
        id: "ID"
      }
    }
  }
};
