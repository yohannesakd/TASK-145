package com.roadrunner.dispatch.infrastructure.db;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.roadrunner.dispatch.infrastructure.db.entity.DiscountRuleEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.SensitiveWordEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ShippingTemplateEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.UserEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.WorkerEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ZoneEntity;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * RoomDatabase.Callback that populates reference data on first database creation.
 *
 * All inserts run on a single background thread so the main thread is never blocked
 * and Room's "main-thread query" guard is never triggered.
 */
public class SeedDatabaseCallback extends RoomDatabase.Callback {

    private static final String ORG_ID = "default_org";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 32;
    private static final int KEY_LENGTH_BITS = 256;

    // ── Development seed credentials (CHANGE IN PRODUCTION) ──────────────────
    private static final String SEED_ADMIN_PASSWORD      = "Admin12345678";
    private static final String SEED_DISPATCHER_PASSWORD = "Dispatcher1234";
    private static final String SEED_WORKER_PASSWORD     = "Worker12345678";
    private static final String SEED_REVIEWER_PASSWORD   = "Reviewer1234";

    @Override
    public void onCreate(@NonNull SupportSQLiteDatabase db) {
        super.onCreate(db);
        // Obtain a reference to the fully-built database instance and run seed off main thread.
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase database = AppDatabase.INSTANCE;
            if (database != null) {
                seedAll(database);
            }
        });
    }

    // ── Seed entry point ───────────────────────────────────────────────────────

    private void seedAll(AppDatabase database) {
        seedAdminUser(database);
        seedDispatcherUser(database);
        seedWorkerUser(database);
        seedReviewerUser(database);
        seedShippingTemplates(database);
        seedZones(database);
        seedSensitiveWords(database);
        seedProducts(database);
        seedDiscountRules(database);
    }

    // ── Admin user ─────────────────────────────────────────────────────────────

    private void seedAdminUser(AppDatabase database) {
        String[] hashAndSalt = hashPassword(SEED_ADMIN_PASSWORD);
        String passwordHash = hashAndSalt[0];
        String passwordSalt = hashAndSalt[1];

        long now = System.currentTimeMillis();
        UserEntity admin = new UserEntity(
                UUID.randomUUID().toString(),
                ORG_ID,
                "admin",
                passwordHash,
                passwordSalt,
                "ADMIN",
                true,
                0,
                0L,
                now,
                now
        );
        database.userDao().insertUser(admin);
    }

    // ── Dispatcher user ────────────────────────────────────────────────────────

    private void seedDispatcherUser(AppDatabase database) {
        String[] hashAndSalt = hashPassword(SEED_DISPATCHER_PASSWORD);
        long now = System.currentTimeMillis();
        UserEntity dispatcher = new UserEntity(
                UUID.randomUUID().toString(),
                ORG_ID,
                "dispatcher",
                hashAndSalt[0],
                hashAndSalt[1],
                "DISPATCHER",
                true,
                0,
                0L,
                now,
                now
        );
        database.userDao().insertUser(dispatcher);
    }

    // ── Worker user ────────────────────────────────────────────────────────────

    private void seedWorkerUser(AppDatabase database) {
        String[] hashAndSalt = hashPassword(SEED_WORKER_PASSWORD);
        long now = System.currentTimeMillis();
        String workerId = UUID.randomUUID().toString();
        String workerUserId = UUID.randomUUID().toString();
        UserEntity worker = new UserEntity(
                workerUserId,
                ORG_ID,
                "worker",
                hashAndSalt[0],
                hashAndSalt[1],
                "WORKER",
                true,
                0,
                0L,
                now,
                now
        );
        database.userDao().insertUser(worker);

        WorkerEntity workerEntity = new WorkerEntity(
                workerId,
                workerUserId,
                ORG_ID,
                "Seed Worker",
                "AVAILABLE",
                0,
                3.0,
                null,
                now,
                now
        );
        database.workerDao().insert(workerEntity);
    }

    // ── Compliance reviewer user ───────────────────────────────────────────────

    private void seedReviewerUser(AppDatabase database) {
        String[] hashAndSalt = hashPassword(SEED_REVIEWER_PASSWORD);
        long now = System.currentTimeMillis();
        UserEntity reviewer = new UserEntity(
                UUID.randomUUID().toString(),
                ORG_ID,
                "reviewer",
                hashAndSalt[0],
                hashAndSalt[1],
                "COMPLIANCE_REVIEWER",
                true,
                0,
                0L,
                now,
                now
        );
        database.userDao().insertUser(reviewer);
    }

    // ── Shipping templates ─────────────────────────────────────────────────────

    private void seedShippingTemplates(AppDatabase database) {
        List<ShippingTemplateEntity> templates = new ArrayList<>();

        templates.add(new ShippingTemplateEntity(
                UUID.randomUUID().toString(),
                ORG_ID,
                "Standard",
                "Standard delivery in 3–5 business days",
                599L,
                3,
                5,
                false
        ));

        templates.add(new ShippingTemplateEntity(
                UUID.randomUUID().toString(),
                ORG_ID,
                "Expedited",
                "Expedited delivery in 1–2 business days",
                1499L,
                1,
                2,
                false
        ));

        templates.add(new ShippingTemplateEntity(
                UUID.randomUUID().toString(),
                ORG_ID,
                "Local Pickup",
                "Pick up your order at our facility — no shipping cost",
                0L,
                0,
                0,
                true
        ));

        for (ShippingTemplateEntity template : templates) {
            database.shippingTemplateDao().insert(template);
        }
    }

    // ── Zones ──────────────────────────────────────────────────────────────────

    private void seedZones(AppDatabase database) {
        String[][] zoneData = {
            { "Zone A", "1", "Closest / highest priority" },
            { "Zone B", "2", "Near" },
            { "Zone C", "3", "Medium distance" },
            { "Zone D", "4", "Far" },
            { "Zone E", "5", "Farthest / lowest priority" }
        };

        for (String[] z : zoneData) {
            ZoneEntity zone = new ZoneEntity(
                    UUID.randomUUID().toString(),
                    ORG_ID,
                    z[0],
                    Integer.parseInt(z[1]),
                    z[2]
            );
            database.zoneDao().insert(zone);
        }
    }

    // ── Sensitive words ────────────────────────────────────────────────────────

    private void seedSensitiveWords(AppDatabase database) {
        long now = System.currentTimeMillis();

        String[] regularWords = {
            "spam", "scam", "fraud", "inappropriate", "offensive",
            "misleading", "deceptive", "harassment", "discriminate", "exploit"
        };

        String[] zeroToleranceWords = {
            "threat", "violence", "kill", "attack", "bomb", "terrorize"
        };

        List<SensitiveWordEntity> words = new ArrayList<>();

        for (String word : regularWords) {
            words.add(new SensitiveWordEntity(
                    UUID.randomUUID().toString(),
                    word.toLowerCase(),
                    false,
                    now
            ));
        }

        for (String word : zeroToleranceWords) {
            words.add(new SensitiveWordEntity(
                    UUID.randomUUID().toString(),
                    word.toLowerCase(),
                    true,
                    now
            ));
        }

        database.sensitiveWordDao().insertAll(words);
    }

    // ── Products ───────────────────────────────────────────────────────────────

    private void seedProducts(AppDatabase database) {
        long now = System.currentTimeMillis();
        Object[][] products = {
            { "Dispatch Vest",            4999L, false },
            { "Safety Helmet",            2999L, false },
            { "Regulated Chemical Kit",  15999L, true  },
            { "Work Gloves (5-pack)",     1299L, false },
            { "High-Vis Jacket",          5999L, false },
        };
        for (Object[] p : products) {
            String name = (String) p[0];
            long price   = (long) p[1];
            boolean reg  = (boolean) p[2];
            ProductEntity entity = new ProductEntity(
                    UUID.randomUUID().toString(),
                    ORG_ID,
                    name,
                    "RoadRunner",        // brand
                    "General",           // series
                    name,                // model (same as name for seed data)
                    name + " — seed product",
                    price,
                    0.0,                 // tax rate
                    reg,
                    "ACTIVE",
                    null,                // imageUri
                    now,
                    now
            );
            database.productDao().insert(entity);
        }
    }

    // ── Discount rules ─────────────────────────────────────────────────────────

    private void seedDiscountRules(AppDatabase database) {
        long now = System.currentTimeMillis();
        DiscountRuleEntity rule = new DiscountRuleEntity(
                UUID.randomUUID().toString(),
                ORG_ID,
                "Employee Discount",
                "PERCENT_OFF",
                10.0,
                "ACTIVE",
                now
        );
        database.discountRuleDao().insert(rule);
    }

    // ── PBKDF2 helper ──────────────────────────────────────────────────────────

    /**
     * Hashes a plaintext password using PBKDF2WithHmacSHA256.
     *
     * @param plaintext the raw password
     * @return [0] = Base64-encoded hash, [1] = Base64-encoded salt
     */
    static String[] hashPassword(String plaintext) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_BYTES];
            random.nextBytes(salt);

            PBEKeySpec spec = new PBEKeySpec(
                    plaintext.toCharArray(),
                    salt,
                    PBKDF2_ITERATIONS,
                    KEY_LENGTH_BITS
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();

            String encodedHash = Base64.encodeToString(hash, Base64.NO_WRAP);
            String encodedSalt = Base64.encodeToString(salt, Base64.NO_WRAP);
            return new String[]{ encodedHash, encodedSalt };

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // Fallback: store a placeholder so the app can still launch.
            // This branch should never be reached on standard Android runtimes.
            throw new RuntimeException("PBKDF2WithHmacSHA256 not available on this device", e);
        }
    }
}
