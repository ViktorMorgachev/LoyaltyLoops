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
        status_not_paid: "Не оплачена"
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
        dashboard: "Мои Точки",
        create_business: "Создать Бизнес",
        settings: "Настройки Бизнеса",
        profile: "Профиль",
        admin_partners: "Все Партнеры",
        logout: "Выйти"
      },
      // --- НАСТРОЙКИ БИЗНЕСА (Которых не хватало) ---
      settings: {
        title: "Настройки Бизнеса",
        name_label: "Название бренда",
        color_label: "Фирменный цвет",
        color_helper: "Этот цвет будет отображаться на карте клиента",
        logo_label: "Ссылка на логотип",
        save_success: "Настройки успешно сохранены"
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
        // В React i18next интерполяция делается через {{ }}
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
            VISIT_COUNTER: "Счетчик (N-й в подарок)"
        }
      },
      profile: {
        title: "Мой Профиль",
        id_label: "ID Пользователя (нажмите для копирования)",
        phone_label: "Телефон",
        name_label: "Имя",
        save_btn: "Сохранить изменения"
      },
      admin: {
        title: "Управление Партнерами",
        table_owner: "Владелец (ID)",
        table_country: "Страна"
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
        status_not_paid: "Not Paid"
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
        dashboard: "My Points",
        create_business: "Create Business",
        settings: "Business Settings",
        profile: "Profile",
        admin_partners: "All Partners",
        logout: "Logout"
      },
      settings: {
        title: "Business Settings",
        name_label: "Brand Name",
        color_label: "Brand Color",
        color_helper: "This color will be displayed on the client card",
        logo_label: "Logo URL",
        save_success: "Settings saved"
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
            VISIT_COUNTER: "Visits (Nth free)"
        }
      },
      profile: {
        title: "My Profile",
        id_label: "User ID (Click to copy)",
        phone_label: "Phone",
        name_label: "Name",
        save_btn: "Save Changes"
      },
      admin: {
        title: "Partner Management",
        table_owner: "Owner (ID)",
        table_country: "Country"
      }
    }
  }
};