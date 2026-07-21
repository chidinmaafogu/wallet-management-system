INSERT INTO accounts (institution_code, account_number, user_id, account_type, currency, status, created_at)
VALUES ('000999', '0000000000', NULL, 'SYSTEM', 'NGN', 'ACTIVE', CURRENT_TIMESTAMP);

INSERT INTO wallet_balances (account_id, balance, version, updated_at)
SELECT id, 0, 0, CURRENT_TIMESTAMP FROM accounts WHERE account_number = '0000000000';
