package com.roadrunner.dispatch.infrastructure.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.roadrunner.dispatch.infrastructure.db.converter.Converters;
import com.roadrunner.dispatch.infrastructure.db.dao.AuditLogDao;
import com.roadrunner.dispatch.infrastructure.db.dao.CartDao;
import com.roadrunner.dispatch.infrastructure.db.dao.CartItemDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ComplianceCaseDao;
import com.roadrunner.dispatch.infrastructure.db.dao.DiscountRuleDao;
import com.roadrunner.dispatch.infrastructure.db.dao.EmployerDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderDiscountDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderItemDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ProductDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ReportDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ReputationEventDao;
import com.roadrunner.dispatch.infrastructure.db.dao.SensitiveWordDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ShippingTemplateDao;
import com.roadrunner.dispatch.infrastructure.db.dao.TaskAcceptanceDao;
import com.roadrunner.dispatch.infrastructure.db.dao.TaskDao;
import com.roadrunner.dispatch.infrastructure.db.dao.UserDao;
import com.roadrunner.dispatch.infrastructure.db.dao.WorkerDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ZoneDao;
import com.roadrunner.dispatch.infrastructure.db.entity.AuditLogEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.CartEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.CartItemEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ComplianceCaseEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.DiscountRuleEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.EmployerEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderDiscountEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderItemEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ReportEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ReputationEventEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.SensitiveWordEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ShippingTemplateEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskAcceptanceEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.UserEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.WorkerEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ZoneEntity;

@Database(
    entities = {
        UserEntity.class,
        ProductEntity.class,
        CartEntity.class,
        CartItemEntity.class,
        OrderEntity.class,
        OrderItemEntity.class,
        OrderDiscountEntity.class,
        ShippingTemplateEntity.class,
        DiscountRuleEntity.class,
        EmployerEntity.class,
        ComplianceCaseEntity.class,
        AuditLogEntity.class,
        ReportEntity.class,
        WorkerEntity.class,
        TaskEntity.class,
        TaskAcceptanceEntity.class,
        ZoneEntity.class,
        ReputationEventEntity.class,
        SensitiveWordEntity.class
    },
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    // Package-private so SeedDatabaseCallback (same package) can read the
    // already-built instance without causing a circular getInstance() call.
    static volatile AppDatabase INSTANCE;

    // ── DAO accessors ──────────────────────────────────────────────────────────

    public abstract UserDao userDao();
    public abstract ProductDao productDao();
    public abstract CartDao cartDao();
    public abstract CartItemDao cartItemDao();
    public abstract OrderDao orderDao();
    public abstract OrderItemDao orderItemDao();
    public abstract OrderDiscountDao orderDiscountDao();
    public abstract ShippingTemplateDao shippingTemplateDao();
    public abstract DiscountRuleDao discountRuleDao();
    public abstract EmployerDao employerDao();
    public abstract ComplianceCaseDao complianceCaseDao();
    public abstract AuditLogDao auditLogDao();
    public abstract ReportDao reportDao();
    public abstract WorkerDao workerDao();
    public abstract TaskDao taskDao();
    public abstract TaskAcceptanceDao taskAcceptanceDao();
    public abstract ZoneDao zoneDao();
    public abstract ReputationEventDao reputationEventDao();
    public abstract SensitiveWordDao sensitiveWordDao();

    // ── Singleton ──────────────────────────────────────────────────────────────

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    net.sqlcipher.database.SQLiteDatabase.loadLibs(context);
                    String passphrase = getOrCreatePassphrase(context);
                    net.sqlcipher.database.SupportFactory factory =
                        new net.sqlcipher.database.SupportFactory(
                            passphrase.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "roadrunner_db"
                    )
                    .openHelperFactory(factory)
                    .addCallback(new SeedDatabaseCallback())
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    private static String getOrCreatePassphrase(Context context) {
        try {
            String masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(
                androidx.security.crypto.MasterKeys.AES256_GCM_SPEC);
            android.content.SharedPreferences prefs =
                androidx.security.crypto.EncryptedSharedPreferences.create(
                    "roadrunner_db_key_prefs", masterKeyAlias,
                    context.getApplicationContext(),
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            String stored = prefs.getString("db_passphrase", null);
            if (stored != null) return stored;
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
            String passphrase = android.util.Base64.encodeToString(random, android.util.Base64.NO_WRAP);
            prefs.edit().putString("db_passphrase", passphrase).apply();
            return passphrase;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create database encryption key", e);
        }
    }
}
