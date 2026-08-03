package com.nudge.app.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class NudgeDatabase_Impl extends NudgeDatabase {
  private volatile DataPointDao _dataPointDao;

  private volatile PhysicianConnectionDao _physicianConnectionDao;

  private volatile UserStatsDao _userStatsDao;

  private volatile UserDao _userDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(4) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `data_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `username` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `value` REAL NOT NULL, `type` TEXT NOT NULL, `isSynced` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `physician_connections` (`physicianEmail` TEXT NOT NULL, `patientUsername` TEXT NOT NULL, `physicianName` TEXT NOT NULL, `connectionDate` INTEGER NOT NULL, `status` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`physicianEmail`, `patientUsername`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_stats` (`username` TEXT NOT NULL, `highScore` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`username`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`username` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `role` TEXT NOT NULL, PRIMARY KEY(`username`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '66b2626e32547bb067e90ccb8775abc5')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `data_points`");
        db.execSQL("DROP TABLE IF EXISTS `physician_connections`");
        db.execSQL("DROP TABLE IF EXISTS `user_stats`");
        db.execSQL("DROP TABLE IF EXISTS `users`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsDataPoints = new HashMap<String, TableInfo.Column>(6);
        _columnsDataPoints.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDataPoints.put("username", new TableInfo.Column("username", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDataPoints.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDataPoints.put("value", new TableInfo.Column("value", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDataPoints.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDataPoints.put("isSynced", new TableInfo.Column("isSynced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDataPoints = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDataPoints = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDataPoints = new TableInfo("data_points", _columnsDataPoints, _foreignKeysDataPoints, _indicesDataPoints);
        final TableInfo _existingDataPoints = TableInfo.read(db, "data_points");
        if (!_infoDataPoints.equals(_existingDataPoints)) {
          return new RoomOpenHelper.ValidationResult(false, "data_points(com.nudge.app.data.DataPoint).\n"
                  + " Expected:\n" + _infoDataPoints + "\n"
                  + " Found:\n" + _existingDataPoints);
        }
        final HashMap<String, TableInfo.Column> _columnsPhysicianConnections = new HashMap<String, TableInfo.Column>(6);
        _columnsPhysicianConnections.put("physicianEmail", new TableInfo.Column("physicianEmail", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhysicianConnections.put("patientUsername", new TableInfo.Column("patientUsername", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhysicianConnections.put("physicianName", new TableInfo.Column("physicianName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhysicianConnections.put("connectionDate", new TableInfo.Column("connectionDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhysicianConnections.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhysicianConnections.put("isSynced", new TableInfo.Column("isSynced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPhysicianConnections = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPhysicianConnections = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPhysicianConnections = new TableInfo("physician_connections", _columnsPhysicianConnections, _foreignKeysPhysicianConnections, _indicesPhysicianConnections);
        final TableInfo _existingPhysicianConnections = TableInfo.read(db, "physician_connections");
        if (!_infoPhysicianConnections.equals(_existingPhysicianConnections)) {
          return new RoomOpenHelper.ValidationResult(false, "physician_connections(com.nudge.app.data.PhysicianConnection).\n"
                  + " Expected:\n" + _infoPhysicianConnections + "\n"
                  + " Found:\n" + _existingPhysicianConnections);
        }
        final HashMap<String, TableInfo.Column> _columnsUserStats = new HashMap<String, TableInfo.Column>(3);
        _columnsUserStats.put("username", new TableInfo.Column("username", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserStats.put("highScore", new TableInfo.Column("highScore", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserStats.put("isSynced", new TableInfo.Column("isSynced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserStats = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserStats = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserStats = new TableInfo("user_stats", _columnsUserStats, _foreignKeysUserStats, _indicesUserStats);
        final TableInfo _existingUserStats = TableInfo.read(db, "user_stats");
        if (!_infoUserStats.equals(_existingUserStats)) {
          return new RoomOpenHelper.ValidationResult(false, "user_stats(com.nudge.app.data.UserStats).\n"
                  + " Expected:\n" + _infoUserStats + "\n"
                  + " Found:\n" + _existingUserStats);
        }
        final HashMap<String, TableInfo.Column> _columnsUsers = new HashMap<String, TableInfo.Column>(3);
        _columnsUsers.put("username", new TableInfo.Column("username", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUsers.put("passwordHash", new TableInfo.Column("passwordHash", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUsers.put("role", new TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUsers = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUsers = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUsers = new TableInfo("users", _columnsUsers, _foreignKeysUsers, _indicesUsers);
        final TableInfo _existingUsers = TableInfo.read(db, "users");
        if (!_infoUsers.equals(_existingUsers)) {
          return new RoomOpenHelper.ValidationResult(false, "users(com.nudge.app.data.User).\n"
                  + " Expected:\n" + _infoUsers + "\n"
                  + " Found:\n" + _existingUsers);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "66b2626e32547bb067e90ccb8775abc5", "f07a5a1164ad37097bbb53b2ab092cc3");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "data_points","physician_connections","user_stats","users");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `data_points`");
      _db.execSQL("DELETE FROM `physician_connections`");
      _db.execSQL("DELETE FROM `user_stats`");
      _db.execSQL("DELETE FROM `users`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(DataPointDao.class, DataPointDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PhysicianConnectionDao.class, PhysicianConnectionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UserStatsDao.class, UserStatsDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UserDao.class, UserDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public DataPointDao dataPointDao() {
    if (_dataPointDao != null) {
      return _dataPointDao;
    } else {
      synchronized(this) {
        if(_dataPointDao == null) {
          _dataPointDao = new DataPointDao_Impl(this);
        }
        return _dataPointDao;
      }
    }
  }

  @Override
  public PhysicianConnectionDao physicianConnectionDao() {
    if (_physicianConnectionDao != null) {
      return _physicianConnectionDao;
    } else {
      synchronized(this) {
        if(_physicianConnectionDao == null) {
          _physicianConnectionDao = new PhysicianConnectionDao_Impl(this);
        }
        return _physicianConnectionDao;
      }
    }
  }

  @Override
  public UserStatsDao userStatsDao() {
    if (_userStatsDao != null) {
      return _userStatsDao;
    } else {
      synchronized(this) {
        if(_userStatsDao == null) {
          _userStatsDao = new UserStatsDao_Impl(this);
        }
        return _userStatsDao;
      }
    }
  }

  @Override
  public UserDao userDao() {
    if (_userDao != null) {
      return _userDao;
    } else {
      synchronized(this) {
        if(_userDao == null) {
          _userDao = new UserDao_Impl(this);
        }
        return _userDao;
      }
    }
  }
}
