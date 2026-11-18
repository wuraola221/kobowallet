package com.kobowallet.kobowallet;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class LedgerIntegrityTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void ledgerShouldRejectTruncate() {

        assertThrows(
                DataAccessException.class, () -> jdbcTemplate.execute(
                        "TRUNCATE TABLE ledger_entries"
                )
        );
    }



    @Test
    @Transactional
    void ledgerShouldRejectUpdate() {

        UUID debitEntryId = createValidTransferAndLedgerEntries();

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                        UPDATE ledger_entries
                        SET amount = 1500.00
                        WHERE id = ?
                        """,
                        debitEntryId
                )
        );
    }

    @Test
    @Transactional
    void ledgerShouldRejectDelete() {

        UUID debitEntryId = createValidTransferAndLedgerEntries();

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                        DELETE FROM ledger_entries
                        WHERE id = ?
                        """,
                        debitEntryId
                )
        );
    }




    private UUID createValidTransferAndLedgerEntries() {

        UUID senderUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "sender-" + UUID.randomUUID() + "@example.com",
                "Test Sender"
        );

        UUID recipientUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "recipient-" + UUID.randomUUID() + "@example.com",
                "Test Recipient"
        );

        UUID senderWalletId = jdbcTemplate.queryForObject(
                """
                INSERT INTO wallets (user_id, balance, currency)
                VALUES (?, 10000.00, 'NGN')
                RETURNING id
                """,
                UUID.class,
                senderUserId
        );

        UUID recipientWalletId = jdbcTemplate.queryForObject(
                """
                INSERT INTO wallets (user_id, balance, currency)
                VALUES (?, 5000.00, 'NGN')
                RETURNING id
                """,
                UUID.class,
                recipientUserId
        );

        UUID transferId = jdbcTemplate.queryForObject(
                """
                INSERT INTO transfers (
                    sender_wallet_id,
                    recipient_wallet_id,
                    amount,
                    currency,
                    idempotency_key,
                    request_hash
                )
                VALUES (?, ?, 2000.00, 'NGN', ?, ?)
                RETURNING id
                """,
                UUID.class,
                senderWalletId,
                recipientWalletId,
                "test-" + UUID.randomUUID(),
                "a".repeat(64)
        );

        UUID debitEntryId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO ledger_entries (
                    id, transfer_id, wallet_id, entry_type, amount, currency
                )
                VALUES (?, ?, ?, 'DEBIT', 2000.00, 'NGN')
                """,
                debitEntryId,
                transferId,
                senderWalletId
        );

        jdbcTemplate.update(
                """
                INSERT INTO ledger_entries (
                    transfer_id, wallet_id, entry_type, amount, currency
                )
                VALUES (?, ?, 'CREDIT', 2000.00, 'NGN')
                """,
                transferId,
                recipientWalletId
        );

        return debitEntryId;
    }


    @Test
    @Transactional
    void transferShouldRejectCommitWithoutLedgerEntries() {

        UUID senderUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "incomplete-sender-" + UUID.randomUUID() + "@example.com",
                "Incomplete Transfer Sender"
        );

        UUID recipientUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "incomplete-recipient-" + UUID.randomUUID() + "@example.com",
                "Incomplete Transfer Recipient"
        );

        UUID senderWalletId = jdbcTemplate.queryForObject(
                """
                INSERT INTO wallets (user_id, balance, currency)
                VALUES (?, 10000.00, 'NGN')
                RETURNING id
                """,
                UUID.class,
                senderUserId
        );

        UUID recipientWalletId = jdbcTemplate.queryForObject(
                """
                INSERT INTO wallets (user_id, balance, currency)
                VALUES (?, 5000.00, 'NGN')
                RETURNING id
                """,
                UUID.class,
                recipientUserId
        );

        UUID transferId = jdbcTemplate.queryForObject(
                """
                INSERT INTO transfers (
                    sender_wallet_id,
                    recipient_wallet_id,
                    amount,
                    currency,
                    idempotency_key,
                    request_hash
                )
                VALUES (?, ?, 2000.00, 'NGN', ?, ?)
                RETURNING id
                """,
                UUID.class,
                senderWalletId,
                recipientWalletId,
                "incomplete-" + UUID.randomUUID(),
                "a".repeat(64)
        );

        assertNotNull(transferId);

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE")
        );
    }

    @Test
    @Transactional
    void transferShouldAcceptExactlyTwoMatchingLedgerEntries() {

        createValidTransferAndLedgerEntries();

        jdbcTemplate.execute(
                "SET CONSTRAINTS trg_validate_transfer_ledger, trg_validate_ledger_entries IMMEDIATE"
        );
    }


    @Test
    @Transactional
    void transferShouldRejectMismatchedLedgerAmounts() {

        UUID senderUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "mismatch-sender-" + UUID.randomUUID() + "@example.com",
                "Mismatch Sender"
        );

        UUID recipientUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "mismatch-recipient-" + UUID.randomUUID() + "@example.com",
                "Mismatch Recipient"
        );

        UUID senderWalletId = jdbcTemplate.queryForObject(
                """
                INSERT INTO wallets (user_id, balance, currency)
                VALUES (?, 10000.00, 'NGN')
                RETURNING id
                """,
                UUID.class,
                senderUserId
        );

        UUID recipientWalletId = jdbcTemplate.queryForObject(
                """
                INSERT INTO wallets (user_id, balance, currency)
                VALUES (?, 5000.00, 'NGN')
                RETURNING id
                """,
                UUID.class,
                recipientUserId
        );

        UUID transferId = jdbcTemplate.queryForObject(
                """
                INSERT INTO transfers (
                    sender_wallet_id,
                    recipient_wallet_id,
                    amount,
                    currency,
                    idempotency_key,
                    request_hash
                )
                VALUES (?, ?, 2000.00, 'NGN', ?, ?)
                RETURNING id
                """,
                UUID.class,
                senderWalletId,
                recipientWalletId,
                "mismatch-" + UUID.randomUUID(),
                "a".repeat(64)
        );

        jdbcTemplate.update(
                """
                INSERT INTO ledger_entries (
                    transfer_id, wallet_id, entry_type, amount, currency
                )
                VALUES (?, ?, 'DEBIT', 2000.00, 'NGN')
                """,
                transferId,
                senderWalletId
        );

        jdbcTemplate.update(
                """
                INSERT INTO ledger_entries (
                    transfer_id, wallet_id, entry_type, amount, currency
                )
                VALUES (?, ?, 'CREDIT', 1500.00, 'NGN')
                """,
                transferId,
                recipientWalletId
        );

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.execute(
                        "SET CONSTRAINTS trg_validate_transfer_ledger, trg_validate_ledger_entries IMMEDIATE"
                )
        );
    }

    @Test
    @Transactional
    void transferShouldRejectLedgerEntryForWrongWallet() {

        UUID senderUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "wrong-wallet-sender-" + UUID.randomUUID() + "@example.com",
                "Wrong Wallet Sender"
        );

        UUID recipientUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "wrong-wallet-recipient-" + UUID.randomUUID() + "@example.com",
                "Wrong Wallet Recipient"
        );

        UUID thirdUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "wrong-wallet-third-" + UUID.randomUUID() + "@example.com",
                "Third User"
        );

        UUID senderWalletId = createWallet(senderUserId);
        UUID recipientWalletId = createWallet(recipientUserId);
        UUID thirdWalletId = createWallet(thirdUserId);

        UUID transferId = jdbcTemplate.queryForObject(
                """
                INSERT INTO transfers (
                    sender_wallet_id,
                    recipient_wallet_id,
                    amount,
                    currency,
                    idempotency_key,
                    request_hash
                )
                VALUES (?, ?, 2000.00, 'NGN', ?, ?)
                RETURNING id
                """,
                UUID.class,
                senderWalletId,
                recipientWalletId,
                "wrong-wallet-" + UUID.randomUUID(),
                "a".repeat(64)
        );

        jdbcTemplate.update(
                """
                INSERT INTO ledger_entries (
                    transfer_id, wallet_id, entry_type, amount, currency
                )
                VALUES (?, ?, 'DEBIT', 2000.00, 'NGN')
                """,
                transferId,
                senderWalletId
        );

        jdbcTemplate.update(
                """
                INSERT INTO ledger_entries (
                    transfer_id, wallet_id, entry_type, amount, currency
                )
                VALUES (?, ?, 'CREDIT', 2000.00, 'NGN')
                """,
                transferId,
                thirdWalletId
        );

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.execute(
                        "SET CONSTRAINTS trg_validate_transfer_ledger, trg_validate_ledger_entries IMMEDIATE"
                )
        );
    }

    private UUID createWallet(UUID userId) {

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO wallets (user_id, balance, currency)
                VALUES (?, 5000.00, 'NGN')
                RETURNING id
                """,
                UUID.class,
                userId
        );
    }

    @Test
    @Transactional
    void walletShouldRejectNonNgnCurrency() {

        UUID userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "currency-wallet-" + UUID.randomUUID() + "@example.com",
                "Currency Test User"
        );

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO wallets (user_id, balance, currency)
                        VALUES (?, 5000.00, 'USD')
                        """,
                        userId
                )
        );
    }

    @Test
    @Transactional
    void transferShouldRejectNonNgnCurrency() {

        UUID senderUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "currency-transfer-sender-" + UUID.randomUUID() + "@example.com",
                "Currency Transfer Sender"
        );

        UUID recipientUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "currency-transfer-recipient-" + UUID.randomUUID() + "@example.com",
                "Currency Transfer Recipient"
        );

        UUID senderWalletId = createWallet(senderUserId);
        UUID recipientWalletId = createWallet(recipientUserId);

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO transfers (
                            sender_wallet_id,
                            recipient_wallet_id,
                            amount,
                            currency,
                            idempotency_key,
                            request_hash
                        )
                        VALUES (?, ?, 2000.00, 'USD', ?, ?)
                        """,
                        senderWalletId,
                        recipientWalletId,
                        "currency-transfer-" + UUID.randomUUID(),
                        "a".repeat(64)
                )
        );
    }

    @Test
    @Transactional
    void ledgerEntryShouldRejectNonNgnCurrency() {

        UUID senderUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "currency-ledger-sender-" + UUID.randomUUID() + "@example.com",
                "Currency Ledger Sender"
        );

        UUID recipientUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
                UUID.class,
                "currency-ledger-recipient-" + UUID.randomUUID() + "@example.com",
                "Currency Ledger Recipient"
        );

        UUID senderWalletId = createWallet(senderUserId);
        UUID recipientWalletId = createWallet(recipientUserId);

        UUID transferId = jdbcTemplate.queryForObject(
                """
                INSERT INTO transfers (
                    sender_wallet_id,
                    recipient_wallet_id,
                    amount,
                    currency,
                    idempotency_key,
                    request_hash
                )
                VALUES (?, ?, 2000.00, 'NGN', ?, ?)
                RETURNING id
                """,
                UUID.class,
                senderWalletId,
                recipientWalletId,
                "currency-ledger-" + UUID.randomUUID(),
                "a".repeat(64)
        );

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO ledger_entries (
                            transfer_id,
                            wallet_id,
                            entry_type,
                            amount,
                            currency
                        )
                        VALUES (?, ?, 'DEBIT', 2000.00, 'USD')
                        """,
                        transferId,
                        senderWalletId
                )
        );
    }
}
