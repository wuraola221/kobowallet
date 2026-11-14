package com.kobowallet.kobowallet;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KobowalletApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void usersTableShouldExist() {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*)
						FROM information_schema.tables
						WHERE table_schema = 'public'
						AND table_name = ?
						""",
				Integer.class,
				"users"
		);
		assertEquals(1, count);
	}

	@Test
	void walletsTableShouldExist (){
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*)
						FROM information_schema.tables
						WHERE table_schema = 'public'
						AND table_name = ?
						""",
				Integer.class,
				"wallets"
		);
		assertEquals(1, count);
	}

	@Test
	void flywayShouldHaveAppliedV1Migration() {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*)
						FROM flyway_schema_history
						WHERE version = ?
						AND success = true
						""",
				Integer.class,
				"1"
		);
				assertEquals(1, count);
	}

	@Test
	void usersEmailIndexShouldBeUniqueAndCaseInsensitive(){
		String indexDefinition = jdbcTemplate.queryForObject(
				"""
						SELECT indexdef
						FROM pg_indexes
						WHERE schemaname = 'public'
						AND tablename = 'users'
						AND indexname = ?
						""",
				String.class,
				"idx_users_email_unique"
		);

		assertTrue(indexDefinition.contains("UNIQUE"));
		assertTrue(indexDefinition.toLowerCase().contains("lower("));
		assertTrue(indexDefinition.toLowerCase().contains("email"));
	}

	@Test
	void walletsUserForeignKeyShouldExist() {
		Integer count = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname = ?
                AND contype = 'f'
                AND conrelid = 'public.wallets'::regclass
                """,
				Integer.class,
				"fk_wallets_user"
		);

		assertEquals(1, count);
	}

	@Test
	void walletsUserConstraintShouldBeUnique() {
		Integer count = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname = ?
                AND contype = 'u'
                AND conrelid = 'public.wallets'::regclass
                """,
				Integer.class,
				"uq_wallets_user"
		);

		assertEquals(1, count);
	}

	@Test
	void usersRoleCheckConstraintShouldExist() {
		Integer count = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname = ?
                AND contype = 'c'
                AND conrelid = 'public.users'::regclass
                """,
				Integer.class,
				"chk_users_role"
		);

		assertEquals(1, count);
	}

	@Test
	void walletsBalanceCheckConstraintShouldExist() {
		Integer count = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname = ?
                AND contype = 'c'
                AND conrelid = 'public.wallets'::regclass
                """,
				Integer.class,
				"chk_wallets_balance"
		);

		assertEquals(1, count);
	}

	@Test
	void walletsCurrencyCheckConstraintShouldExist() {
		Integer count = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname = ?
                AND contype = 'c'
                AND conrelid = 'public.wallets'::regclass
                """,
				Integer.class,
				"chk_wallets_currency"
		);

		assertEquals(1, count);
	}

	@Test
	@Transactional
	void usersShouldRejectDuplicateEmailsRegardlessOfCase() {
		String email = "test-" + UUID.randomUUID() + "@example.com";
		String duplicateEmail = email.toUpperCase(Locale.ROOT);

		jdbcTemplate.update(
				"""
						INSERT INTO users (email, full_name)
						VALUES (?, ? )
						""",
				email,
				"First test user"
		);

		assertThrows(
				DuplicateKeyException.class,
				() -> jdbcTemplate.update(
						"""
								INSERT INTO users (email, full_name)
								VALUES (?, ? )
								""",
						duplicateEmail,
						"Second test user"
				)
		);
	}

	@Test
	@Transactional
	void usersShouldRejectInvalidRoles() {
		String email = "test-" + UUID.randomUUID() + "@example.com";

		assertThrows (
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update(
				"""
						INSERT INTO users (email, full_name, role)
						VALUES(?, ?, ?)
						""",
				email,
				"Invalid role",
				"GUEST"

				)
		);
	}

	@Test
	@Transactional
	void walletsShouldRejectNegativeBalance() {
		String email = "test-" + UUID.randomUUID() + "@example.com";

		UUID userId = jdbcTemplate.queryForObject(
				"""
						INSERT INTO users (email, full_name)
						VALUES (?, ?)
						RETURNING id
						""",
				UUID.class,
				email,
				"Negative balance test user"
		);
		assertThrows (
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update(
						"""
								INSERT INTO wallets (user_id, balance)
								VALUES (?, ?)
								""",
						userId,
						new BigDecimal("-100.00")
				)
		);
	}

	@Test
	@Transactional
	void walletsShouldRejectMultipleWalletsForSameUser() {
		String email = "test-" + UUID.randomUUID() + "@example.com";

		UUID userId = jdbcTemplate.queryForObject(
				"""
                INSERT INTO users (email, full_name)
                VALUES (?, ?)
                RETURNING id
                """,
				UUID.class,
				email,
				"Duplicate Wallet Test User"
		);

		jdbcTemplate.update(
				"""
                INSERT INTO wallets (user_id, balance)
                VALUES (?, ?)
                """,
				userId,
				new BigDecimal("1000.00")
		);

		assertThrows(
				DuplicateKeyException.class,
				() -> jdbcTemplate.update(
						"""
                        INSERT INTO wallets (user_id, balance)
                        VALUES (?, ?)
                        """,
						userId,
						new BigDecimal("500.00")
				)
		);
	}

	@Test
	@Transactional
	void walletsShouldRejectNonExistentUsers() {
		UUID nonExistentUserId = UUID.randomUUID();

		assertThrows(
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update(
						"""
                        INSERT INTO wallets (user_id, balance)
                        VALUES (?, ?)
                        """,
						nonExistentUserId,
						new BigDecimal("100.00")
				)
		);
	}
}
