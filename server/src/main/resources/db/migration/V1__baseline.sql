-- Baseline: полная схема на момент перехода с SchemaUtils.createMissingTablesAndColumns на Flyway.
-- На существующих БД не выполняется (baselineOnMigrate=true), исполняется только на пустых.

CREATE TABLE IF NOT EXISTS users (
    id              uuid PRIMARY KEY,
    phone_number    varchar(20)  NOT NULL,
    country_code    varchar(4)   NOT NULL DEFAULT 'KG',
    created_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    first_name      varchar(50)  NULL,
    last_name       varchar(50)  NULL,
    email           varchar(100) NULL,
    qr_secret       varchar(64)  NOT NULL,
    language        varchar(5)   NOT NULL DEFAULT 'ru',
    telegram_id     bigint       NULL,
    frozen_until    timestamp    NULL,
    is_deleted      boolean      NOT NULL DEFAULT FALSE,
    deletion_reason text         NULL,
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_telegram_id UNIQUE (telegram_id)
);

CREATE TABLE IF NOT EXISTS partners (
    id                    uuid PRIMARY KEY,
    owner_id              uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_name         varchar(50)  NOT NULL,
    country_code          varchar(4)   NOT NULL DEFAULT 'KG',
    created_at            timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    admin_pin_hash        varchar(128) NULL,
    logo_url              varchar(255) NULL,
    color                 varchar(9)   NOT NULL DEFAULT '#4F46E5',
    default_visits_target integer      NOT NULL DEFAULT 10,
    status                varchar(20)  NOT NULL DEFAULT 'PENDING',
    burn_bonuses_days     integer      NULL,
    downgrade_tier_days   integer      NULL,
    timezone              varchar(50)  NOT NULL DEFAULT 'UTC',
    manager_invite_code   varchar(20)  NOT NULL,
    base_currency         varchar(3)   NOT NULL DEFAULT 'USD',
    CONSTRAINT uk_partners_manager_invite_code UNIQUE (manager_invite_code)
);

CREATE TABLE IF NOT EXISTS trading_points (
    id                    uuid PRIMARY KEY,
    partner_id            uuid         NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    name                  varchar(100) NOT NULL,
    address               varchar(200) NULL,
    invite_code           varchar(20)  NOT NULL,
    is_active             boolean      NOT NULL DEFAULT FALSE,
    is_temporarily_paused boolean      NOT NULL DEFAULT FALSE,
    type                  varchar(20)  NOT NULL DEFAULT 'OTHER',
    latitude              double precision NULL,
    longitude             double precision NULL,
    currency              varchar(3)   NOT NULL,
    working_hours_json    text         NULL,
    rating                double precision NOT NULL DEFAULT 0.0,
    rating_count          integer      NOT NULL DEFAULT 0,
    contact_phone         varchar(30)  NULL,
    contact_link          varchar(50)  NULL,
    additional_info       varchar(50)  NULL,
    timezone              varchar(30)  NOT NULL DEFAULT 'UTC',
    created_at            timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_trading_points_invite_code UNIQUE (invite_code)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         uuid PRIMARY KEY,
    token      varchar(2048) NOT NULL,
    user_id    uuid          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at timestamp     NOT NULL,
    user_agent varchar(255)  NULL,
    ip_address varchar(45)   NULL,
    created_at timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token)
);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE IF NOT EXISTS loyalty_settings (
    id                     uuid PRIMARY KEY,
    partner_id             uuid        NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    trading_point_id       uuid        NOT NULL REFERENCES trading_points(id) ON DELETE CASCADE,
    program_type           varchar(20) NOT NULL,
    created_at             timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    visits_reward          varchar(100) NULL,
    max_burn_percentage    integer     NOT NULL DEFAULT 100,
    award_on_mixed_payment boolean     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_loyalty_settings_trading_point_id UNIQUE (trading_point_id)
);
CREATE INDEX IF NOT EXISTS ix_loyalty_settings_partner_id ON loyalty_settings (partner_id);

CREATE TABLE IF NOT EXISTS loyalty_tiers (
    id               uuid PRIMARY KEY,
    partner_id       uuid          NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    level_index      integer       NOT NULL,
    name             varchar(50)   NOT NULL,
    threshold        numeric(12,2) NOT NULL DEFAULT 0,
    cashback_percent numeric(5,2)  NOT NULL DEFAULT 0,
    CONSTRAINT uk_loyalty_tiers_partner_level UNIQUE (partner_id, level_index)
);

CREATE TABLE IF NOT EXISTS partner_staff (
    id               uuid PRIMARY KEY,
    user_id          uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    partner_id       uuid        NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    trading_point_id uuid        NULL REFERENCES trading_points(id) ON DELETE SET NULL,
    role             varchar(30) NOT NULL,
    is_active        boolean     NOT NULL DEFAULT TRUE,
    can_refund       boolean     NOT NULL DEFAULT FALSE,
    created_at       timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_partner_staff_user_partner_role_point UNIQUE (user_id, partner_id, role, trading_point_id)
);

CREATE TABLE IF NOT EXISTS system_staff (
    id         uuid PRIMARY KEY,
    user_id    uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       varchar(50)  NOT NULL,
    pin_hash   varchar(128) NULL,
    is_active  boolean      NOT NULL DEFAULT TRUE,
    created_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_staff_user_id UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS transactions_history (
    id                     uuid PRIMARY KEY,
    user_id                uuid          NULL REFERENCES users(id) ON DELETE SET NULL,
    partner_id             uuid          NULL REFERENCES partners(id) ON DELETE CASCADE,
    trading_point_id       uuid          NULL REFERENCES trading_points(id) ON DELETE SET NULL,
    cashier_id             uuid          NULL REFERENCES partner_staff(id) ON DELETE SET NULL,
    type                   varchar(20)   NOT NULL,
    amount                 numeric(10,2) NOT NULL DEFAULT 0,
    points_delta           numeric(10,2) NOT NULL DEFAULT 0,
    visits_delta           integer       NOT NULL DEFAULT 0,
    currency               varchar(3)    NOT NULL DEFAULT 'USD',
    exchange_rate_snapshot numeric(19,6) NOT NULL DEFAULT 1,
    points_base_value      numeric(10,2) NOT NULL DEFAULT 0,
    created_at             timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_tx_history_user_created ON transactions_history (user_id, created_at);
CREATE INDEX IF NOT EXISTS ix_tx_history_point_created ON transactions_history (trading_point_id, created_at);
CREATE INDEX IF NOT EXISTS ix_tx_history_cashier_created ON transactions_history (cashier_id, created_at);

CREATE TABLE IF NOT EXISTS pin_reset_tokens (
    id         uuid PRIMARY KEY,
    partner    uuid         NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    token_hash varchar(128) NOT NULL,
    created_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamp    NOT NULL,
    used_at    timestamp    NULL
);
CREATE INDEX IF NOT EXISTS ix_pin_reset_tokens_expires_at ON pin_reset_tokens (expires_at);

CREATE TABLE IF NOT EXISTS support_threads (
    id                   uuid PRIMARY KEY,
    partner_id           uuid         NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    topic                varchar(200) NULL,
    created_at           timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_snippet varchar(255) NULL,
    last_message_at      timestamp    NULL,
    unread_for_partner   integer      NOT NULL DEFAULT 0,
    unread_for_admin     integer      NOT NULL DEFAULT 0,
    is_closed            boolean      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS support_messages (
    id              uuid PRIMARY KEY,
    thread_id       uuid      NOT NULL REFERENCES support_threads(id) ON DELETE CASCADE,
    sender_user_id  uuid      NULL REFERENCES users(id) ON DELETE SET NULL,
    is_from_partner boolean   NOT NULL,
    content         text      NOT NULL,
    attachments     text      NULL,
    is_read         boolean   NOT NULL DEFAULT FALSE,
    read_at         timestamp NULL,
    created_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_support_messages_thread_created ON support_messages (thread_id, created_at);

CREATE TABLE IF NOT EXISTS device_tokens (
    id               uuid PRIMARY KEY,
    user_id          uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token            varchar(500) NOT NULL,
    platform         varchar(20)  NOT NULL,
    active_role      varchar(50)  NOT NULL,
    partner_id       uuid         NULL REFERENCES partners(id) ON DELETE CASCADE,
    trading_point_id uuid         NULL REFERENCES trading_points(id) ON DELETE CASCADE,
    updated_at       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_tokens_token UNIQUE (token)
);

CREATE TABLE IF NOT EXISTS system_events (
    id                  uuid PRIMARY KEY,
    type                varchar(50) NOT NULL,
    user_id             uuid        NULL REFERENCES users(id) ON DELETE SET NULL,
    partner_id          uuid        NULL REFERENCES partners(id) ON DELETE SET NULL,
    user_phone_snapshot varchar(20) NULL,
    payload             text        NULL,
    created_at          timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_system_events_user_created ON system_events (user_id, created_at);
CREATE INDEX IF NOT EXISTS ix_system_events_partner_created ON system_events (partner_id, created_at);
CREATE INDEX IF NOT EXISTS ix_system_events_type ON system_events (type);

CREATE TABLE IF NOT EXISTS platform_subscriptions (
    id               uuid PRIMARY KEY,
    point_id         uuid          NOT NULL REFERENCES trading_points(id) ON DELETE CASCADE,
    requester_id     uuid          NULL REFERENCES system_staff(id) ON DELETE SET NULL,
    type             varchar(20)   NOT NULL,
    is_trial         boolean       NOT NULL DEFAULT FALSE,
    amount           numeric(10,2) NOT NULL DEFAULT 0,
    start_date       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_date         timestamp     NOT NULL,
    is_active        boolean       NOT NULL DEFAULT TRUE,
    warning_sent_at  timestamp     NULL,
    critical_sent_at timestamp     NULL,
    created_at       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_platform_subscriptions_end_date ON platform_subscriptions (end_date);
CREATE INDEX IF NOT EXISTS ix_platform_subscriptions_point_active ON platform_subscriptions (point_id, is_active);

CREATE TABLE IF NOT EXISTS platform_requests (
    id                uuid PRIMARY KEY,
    type              varchar(30)   NOT NULL,
    status            varchar(20)   NOT NULL,
    requester_id      uuid          NULL REFERENCES system_staff(id) ON DELETE SET NULL,
    approver_id       uuid          NULL REFERENCES system_staff(id) ON DELETE SET NULL,
    target_point_id   uuid          NULL REFERENCES trading_points(id) ON DELETE CASCADE,
    target_partner_id uuid          NULL REFERENCES partners(id) ON DELETE CASCADE,
    amount            numeric(10,2) NULL,
    duration          varchar(20)   NULL,
    is_trial          boolean       NOT NULL DEFAULT FALSE,
    block_reason      varchar(255)  NULL,
    reject_reason     varchar(255)  NULL,
    created_at        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_platform_requests_status ON platform_requests (status);
CREATE INDEX IF NOT EXISTS ix_platform_requests_created_at ON platform_requests (created_at);

CREATE TABLE IF NOT EXISTS platform_invites (
    id         uuid PRIMARY KEY,
    code       varchar(20) NOT NULL,
    role       varchar(50) NOT NULL,
    created_by uuid        NOT NULL REFERENCES system_staff(id) ON DELETE CASCADE,
    is_used    boolean     NOT NULL DEFAULT FALSE,
    used_at    timestamp   NULL,
    used_by    uuid        NULL REFERENCES users(id) ON DELETE SET NULL,
    created_at timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamp   NOT NULL,
    CONSTRAINT uk_platform_invites_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS ix_platform_invites_expires_at ON platform_invites (expires_at);

CREATE TABLE IF NOT EXISTS client_ratings (
    id               uuid PRIMARY KEY,
    partner_id       uuid      NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    trading_point_id uuid      NULL REFERENCES trading_points(id) ON DELETE SET NULL,
    cashier_id       uuid      NULL REFERENCES users(id) ON DELETE SET NULL,
    user_id          uuid      NULL REFERENCES users(id) ON DELETE SET NULL,
    rating           integer   NOT NULL,
    tags             text      NULL,
    comment          text      NULL,
    is_ignored       boolean   NOT NULL DEFAULT FALSE,
    created_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_client_ratings_partner_created ON client_ratings (partner_id, created_at);
CREATE INDEX IF NOT EXISTS ix_client_ratings_point_created ON client_ratings (trading_point_id, created_at);

CREATE TABLE IF NOT EXISTS service_reviews (
    id               uuid PRIMARY KEY,
    partner_id       uuid      NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    trading_point_id uuid      NULL REFERENCES trading_points(id) ON DELETE SET NULL,
    user_id          uuid      NULL REFERENCES users(id) ON DELETE SET NULL,
    rating           integer   NOT NULL,
    tags             text      NULL,
    comment          text      NULL,
    is_read_by_owner boolean   NOT NULL DEFAULT FALSE,
    owner_reply      text      NULL,
    replied_at       timestamp NULL,
    created_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_service_reviews_partner_created ON service_reviews (partner_id, created_at);
CREATE INDEX IF NOT EXISTS ix_service_reviews_point_created ON service_reviews (trading_point_id, created_at);

CREATE TABLE IF NOT EXISTS waitlist (
    id         uuid PRIMARY KEY,
    email      varchar(255) NOT NULL,
    created_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_invited boolean      NOT NULL DEFAULT FALSE,
    invited_at timestamp    NULL,
    CONSTRAINT uk_waitlist_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id          uuid PRIMARY KEY,
    status      varchar(20) NOT NULL DEFAULT 'PENDING',
    telegram_id bigint      NULL,
    phone       varchar(20) NULL,
    user_id     uuid        NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  timestamp   NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_auth_sessions_telegram_id ON auth_sessions (telegram_id);
CREATE INDEX IF NOT EXISTS ix_auth_sessions_expires_at ON auth_sessions (expires_at);

CREATE TABLE IF NOT EXISTS exchange_rates (
    from_currency varchar(3)    NOT NULL,
    to_currency   varchar(3)    NOT NULL,
    rate          numeric(19,6) NOT NULL,
    updated_at    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_exchange_rates PRIMARY KEY (from_currency, to_currency)
);

CREATE TABLE IF NOT EXISTS loyalty_cards (
    id               uuid PRIMARY KEY,
    user_id          uuid           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    partner_id       uuid           NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    balance          numeric(10,2)  NOT NULL DEFAULT 0,
    total_spent      numeric(12,2)  NOT NULL DEFAULT 0,
    tier_level       integer        NOT NULL DEFAULT 1,
    visits_count     integer        NOT NULL DEFAULT 0,
    is_paused        boolean        NOT NULL DEFAULT FALSE,
    pause_reason     varchar(255)   NULL,
    blocked_until    timestamp      NULL,
    blocked_reason   varchar(255)   NULL,
    trust_score      double precision NOT NULL DEFAULT 4.0,
    fraud_flag       boolean        NOT NULL DEFAULT FALSE,
    total_score      integer        NOT NULL DEFAULT 1,
    last_activity_at timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_partner UNIQUE (user_id, partner_id)
);
