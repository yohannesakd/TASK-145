package com.roadrunner.dispatch;

import android.app.Application;

import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;

/**
 * Application subclass for RoadRunner Dispatch.
 *
 * Initialises the Room database on startup so that the seed callback fires on
 * first launch before any screen tries to query data. The database reference is
 * exposed via {@link #getDatabase()} for lightweight access from any component
 * that cannot use dependency injection.
 */
public class RoadRunnerApp extends Application {

    private static RoadRunnerApp instance;
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Eagerly open the database; this triggers SeedDatabaseCallback.onCreate
        // on first install so reference data is available before the first query.
        database = AppDatabase.getInstance(this);
        // Initialise the manual DI container so all ViewModelFactories can resolve
        // repositories and use-cases without a Context reference at call-site.
        ServiceLocator.getInstance(this);
    }

    /** Returns the singleton Application instance. */
    public static RoadRunnerApp getInstance() {
        return instance;
    }

    /** Returns the application-scoped Room database. */
    public AppDatabase getDatabase() {
        return database;
    }
}
