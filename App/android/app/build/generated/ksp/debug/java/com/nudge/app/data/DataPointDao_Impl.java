package com.nudge.app.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DataPointDao_Impl implements DataPointDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DataPoint> __insertionAdapterOfDataPoint;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDataForUser;

  private final SharedSQLiteStatement __preparedStmtOfClearTrainingData;

  public DataPointDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDataPoint = new EntityInsertionAdapter<DataPoint>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `data_points` (`id`,`username`,`timestamp`,`value`,`type`,`sensorId`,`sessionId`,`label`,`isSynced`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DataPoint entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getUsername());
        statement.bindLong(3, entity.getTimestamp());
        statement.bindDouble(4, entity.getValue());
        statement.bindString(5, entity.getType());
        statement.bindLong(6, entity.getSensorId());
        if (entity.getSessionId() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getSessionId());
        }
        if (entity.getLabel() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getLabel());
        }
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(9, _tmp);
      }
    };
    this.__preparedStmtOfDeleteDataForUser = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM data_points WHERE username = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearTrainingData = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM data_points WHERE label IS NOT NULL AND username = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final DataPoint dataPoint, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDataPoint.insert(dataPoint);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteDataForUser(final String username,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteDataForUser.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, username);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteDataForUser.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearTrainingData(final String username,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearTrainingData.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, username);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearTrainingData.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DataPoint>> getAllDataPoints(final String username) {
    final String _sql = "SELECT * FROM data_points WHERE username = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, username);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"data_points"}, new Callable<List<DataPoint>>() {
      @Override
      @NonNull
      public List<DataPoint> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfSensorId = CursorUtil.getColumnIndexOrThrow(_cursor, "sensorId");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<DataPoint> _result = new ArrayList<DataPoint>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DataPoint _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final float _tmpValue;
            _tmpValue = _cursor.getFloat(_cursorIndexOfValue);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final int _tmpSensorId;
            _tmpSensorId = _cursor.getInt(_cursorIndexOfSensorId);
            final Long _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            }
            final String _tmpLabel;
            if (_cursor.isNull(_cursorIndexOfLabel)) {
              _tmpLabel = null;
            } else {
              _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            }
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new DataPoint(_tmpId,_tmpUsername,_tmpTimestamp,_tmpValue,_tmpType,_tmpSensorId,_tmpSessionId,_tmpLabel,_tmpIsSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<DataPoint>> getDataPointsByType(final String username, final String type) {
    final String _sql = "SELECT * FROM data_points WHERE username = ? AND type = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, username);
    _argIndex = 2;
    _statement.bindString(_argIndex, type);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"data_points"}, new Callable<List<DataPoint>>() {
      @Override
      @NonNull
      public List<DataPoint> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfSensorId = CursorUtil.getColumnIndexOrThrow(_cursor, "sensorId");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<DataPoint> _result = new ArrayList<DataPoint>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DataPoint _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final float _tmpValue;
            _tmpValue = _cursor.getFloat(_cursorIndexOfValue);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final int _tmpSensorId;
            _tmpSensorId = _cursor.getInt(_cursorIndexOfSensorId);
            final Long _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            }
            final String _tmpLabel;
            if (_cursor.isNull(_cursorIndexOfLabel)) {
              _tmpLabel = null;
            } else {
              _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            }
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new DataPoint(_tmpId,_tmpUsername,_tmpTimestamp,_tmpValue,_tmpType,_tmpSensorId,_tmpSessionId,_tmpLabel,_tmpIsSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<DataPoint>> getPointsForSession(final long sessionId) {
    final String _sql = "SELECT * FROM data_points WHERE sessionId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"data_points"}, new Callable<List<DataPoint>>() {
      @Override
      @NonNull
      public List<DataPoint> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfSensorId = CursorUtil.getColumnIndexOrThrow(_cursor, "sensorId");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<DataPoint> _result = new ArrayList<DataPoint>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DataPoint _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final float _tmpValue;
            _tmpValue = _cursor.getFloat(_cursorIndexOfValue);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final int _tmpSensorId;
            _tmpSensorId = _cursor.getInt(_cursorIndexOfSensorId);
            final Long _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            }
            final String _tmpLabel;
            if (_cursor.isNull(_cursorIndexOfLabel)) {
              _tmpLabel = null;
            } else {
              _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            }
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new DataPoint(_tmpId,_tmpUsername,_tmpTimestamp,_tmpValue,_tmpType,_tmpSensorId,_tmpSessionId,_tmpLabel,_tmpIsSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<DataPoint>> getLabeledDataForUser(final String username) {
    final String _sql = "SELECT * FROM data_points WHERE label IS NOT NULL AND username = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, username);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"data_points"}, new Callable<List<DataPoint>>() {
      @Override
      @NonNull
      public List<DataPoint> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfSensorId = CursorUtil.getColumnIndexOrThrow(_cursor, "sensorId");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<DataPoint> _result = new ArrayList<DataPoint>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DataPoint _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final float _tmpValue;
            _tmpValue = _cursor.getFloat(_cursorIndexOfValue);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final int _tmpSensorId;
            _tmpSensorId = _cursor.getInt(_cursorIndexOfSensorId);
            final Long _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            }
            final String _tmpLabel;
            if (_cursor.isNull(_cursorIndexOfLabel)) {
              _tmpLabel = null;
            } else {
              _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            }
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new DataPoint(_tmpId,_tmpUsername,_tmpTimestamp,_tmpValue,_tmpType,_tmpSensorId,_tmpSessionId,_tmpLabel,_tmpIsSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
