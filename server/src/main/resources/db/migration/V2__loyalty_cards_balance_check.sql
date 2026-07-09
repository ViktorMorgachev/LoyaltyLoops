-- Страховка на уровне БД к app-уровневой защите от отрицательного баланса.
-- NOT VALID: существующие строки не сканируются, проверяются только новые изменения.
ALTER TABLE loyalty_cards
    ADD CONSTRAINT chk_loyalty_cards_balance_non_negative CHECK (balance >= 0) NOT VALID;
