package com.nudge.app.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
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
public final class TherapySessionDao_Impl implements TherapySessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TherapySession> __insertionAdapterOfTherapySession;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<TherapySession> __updateAdapterOfTherapySession;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSession;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSessionsForUser;

  public TherapySessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTherapySession = new EntityInsertionAdapter<TherapySession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `therapy_sessions` (`sessionId`,`username`,`startTime`,`endTime`,`restPosition`,`isUploaded`,`isSynced`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TherapySession entity) {
        statement.bindLong(1, entity.getSessionId());
        statement.bindString(2, entity.getUsername());
        statement.bindLong(3, entity.getStartTime());
        if (entity.getEndTime() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getEndTime());
        }
        final String _tmp = __converters.fromRestPosition(entity.getRestPosition());
        statement.bindString(5, _tmp);
        final int _tmp_1 = entity.isUploaded() ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        final int _tmp_2 = entity.isSynced() ? 1 : 0;
        statement.bindLong(7, _tmp_2);
      }
    };
    this.__updateAdapterOfTherapySession = new EntityDeletionOrUpdateAdapter<TherapySession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `therapy_sessions` SET `sessionId` = ?,`username` = ?,`startTime` = ?,`endTime` = ?,`restPosition` = ?,`isUploaded` = ?,`isSynced` = ? WHERE `sessionId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TherapySession entity) {
        statement.bindLong(1, entity.getSessionId());
        statement.bindString(2, entity.getUsername());
        statement.bindLong(3, entity.getStartTime());
        if (entity.getEndTime() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getEndTime());
        }
        final String _tmp = __converters.fromRestPosition(entity.getRestPosition());
        statement.bindString(5, _tmp);
        final int _tmp_1 = entity.isUploaded() ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        final int _tmp_2 = entity.isSynced() ? 1 : 0;
        statement.bindLong(7, _tmp_2);
        statement.bindLong(8, entity.getSessionId());
      }
    };
    this.__preparedStmtOfDeleteSession = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM therapy_sessions WHERE sessionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSessionsForUser = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM therapy_sessions WHERE username = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TherapySession session, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTherapySession.insertAndReturnId(session);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final TherapySession session, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTherapySession.handle(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSession(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSession.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfDeleteSession.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSessionsForUser(final String username,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSessionsForUser.acquire();
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
          __preparedStmtOfDeleteSessionsForUser.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getSessionById(final long id,
      final Continuation<? super TherapySession> $completion) {
    final String _sql = "SELECT * FROM therapy_sessions WHERE sessionId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TherapySession>() {
      @Override
      @Nullable
      public TherapySession call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfRestPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "restPosition");
          final int _cursorIndexOfIsUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "isUploaded");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final TherapySession _result;
          if (_cursor.moveToFirst()) {
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final Long _tmpEndTime;
            if (_cursor.isNull(_cursorIndexOfEndTime)) {
              _tmpEndTime = null;
            } else {
              _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            }
            final RestPosition _tmpRestPosition;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfRestPosition);
            _tmpRestPosition = __converters.toRestPosition(_tmp);
            final boolean _tmpIsUploaded;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsUploaded);
            _tmpIsUploaded = _tmp_1 != 0;
            final boolean _tmpIsSynced;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp_2 != 0;
            _result = new TherapySession(_tmpSessionId,_tmpUsername,_tmpStartTime,_tmpEndTime,_tmpRestPosition,_tmpIsUploaded,_tmpIsSynced);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TherapySession>> getSessionsForUser(final String username) {
    final String _sql = "SELECT * FROM therapy_sessions WHERE username = ? ORDER BY startTime DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, username);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"therapy_sessions"}, new Callable<List<TherapySession>>() {
      @Override
      @NonNull
      public List<TherapySession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfRestPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "restPosition");
          final int _cursorIndexOfIsUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "isUploaded");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<TherapySession> _result = new ArrayList<TherapySession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TherapySession _item;
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final Long _tmpEndTime;
            if (_cursor.isNull(_cursorIndexOfEndTime)) {
              _tmpEndTime = null;
            } else {
              _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            }
            final RestPosition _tmpRestPosition;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfRestPosition);
            _tmpRestPosition = __converters.toRestPosition(_tmp);
            final boolean _tmpIsUploaded;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsUploaded);
            _tmpIsUploaded = _tmp_1 != 0;
            final boolean _tmpIsSynced;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp_2 != 0;
            _item = new TherapySession(_tmpSessionId,_tmpUsername,_tmpStartTime,_tmpEndTime,_tmpRestPosition,_tmpIsUploaded,_tmpIsSynced);
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
