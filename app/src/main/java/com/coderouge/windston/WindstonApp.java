package com.coderouge.windston;

import android.app.Application;
import androidx.room.Room;
import androidx.room.RoomDatabase;

public class WindstonApp extends Application {

    public static WindstonApp instance;

    public static AppDatabase database;


    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = Room.databaseBuilder(
                this,
                AppDatabase.class, "main-db"
            ).build();
    }
}
