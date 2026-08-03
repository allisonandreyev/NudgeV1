package com.nudge.app.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
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
import java.lang.IllegalArgumentException;
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
public final class PhysicianConnectionDao_Impl implements PhysicianConnectionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PhysicianConnection> __insertionAdapterOfPhysicianConnection;

  private final EntityDeletionOrUpdateAdapter<PhysicianConnection> __updateAdapterOfPhysicianConnection;

  private final SharedSQLiteStatement __preparedStmtOfDeleteConnection;

  public PhysicianConnectionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPhysicianConnection = new EntityInsertionAdapter<PhysicianConnection>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `physician_connections` (`physicianEmail`,`patientUsername`,`physicianName`,`connectionDate`,`status`,`isSynced`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhysicianConnection entity) {
        statement.bindString(1, entity.getPhysicianEmail());
        statement.bindString(2, entity.getPatientUsername());
        statement.bindString(3, entity.getPhysicianName());
        statement.bindLong(4, entity.getConnectionDate());
        statement.bindString(5, __ConnectionStatus_enumToString(entity.getStatus()));
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(6, _tmp);
      }
    };
    this.__updateAdapterOfPhysicianConnection = new EntityDeletionOrUpdateAdapter<PhysicianConnection>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `physician_connections` SET `physicianEmail` = ?,`patientUsername` = ?,`physicianName` = ?,`connectionDate` = ?,`status` = ?,`isSynced` = ? WHERE `physicianEmail` = ? AND `patientUsername` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhysicianConnection entity) {
        statement.bindString(1, entity.getPhysicianEmail());
        statement.bindString(2, entity.getPatientUsername());
        statement.bindString(3, entity.getPhysicianName());
        statement.bindLong(4, entity.getConnectionDate());
        statement.bindString(5, __ConnectionStatus_enumToString(entity.getStatus()));
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindString(7, entity.getPhysicianEmail());
        statement.bindString(8, entity.getPatientUsername());
      }
    };
    this.__preparedStmtOfDeleteConnection = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM physician_connections WHERE physicianEmail = ? AND patientUsername = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final PhysicianConnection connection,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPhysicianConnection.insert(connection);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateConnection(final PhysicianConnection connection,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfPhysicianConnection.handle(connection);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteConnection(final String email, final String patientUsername,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteConnection.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, email);
        _argIndex = 2;
        _stmt.bindString(_argIndex, patientUsername);
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
          __preparedStmtOfDeleteConnection.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PhysicianConnection>> getConnectionsForPatient(final String patientUsername) {
    final String _sql = "SELECT * FROM physician_connections WHERE patientUsername = ? ORDER BY connectionDate DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientUsername);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"physician_connections"}, new Callable<List<PhysicianConnection>>() {
      @Override
      @NonNull
      public List<PhysicianConnection> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPhysicianEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "physicianEmail");
          final int _cursorIndexOfPatientUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "patientUsername");
          final int _cursorIndexOfPhysicianName = CursorUtil.getColumnIndexOrThrow(_cursor, "physicianName");
          final int _cursorIndexOfConnectionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "connectionDate");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<PhysicianConnection> _result = new ArrayList<PhysicianConnection>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhysicianConnection _item;
            final String _tmpPhysicianEmail;
            _tmpPhysicianEmail = _cursor.getString(_cursorIndexOfPhysicianEmail);
            final String _tmpPatientUsername;
            _tmpPatientUsername = _cursor.getString(_cursorIndexOfPatientUsername);
            final String _tmpPhysicianName;
            _tmpPhysicianName = _cursor.getString(_cursorIndexOfPhysicianName);
            final long _tmpConnectionDate;
            _tmpConnectionDate = _cursor.getLong(_cursorIndexOfConnectionDate);
            final ConnectionStatus _tmpStatus;
            _tmpStatus = __ConnectionStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new PhysicianConnection(_tmpPhysicianEmail,_tmpPatientUsername,_tmpPhysicianName,_tmpConnectionDate,_tmpStatus,_tmpIsSynced);
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
  public Flow<List<PhysicianConnection>> getConnectionsForPhysician(final String physicianEmail) {
    final String _sql = "SELECT * FROM physician_connections WHERE physicianEmail = ? ORDER BY connectionDate DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, physicianEmail);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"physician_connections"}, new Callable<List<PhysicianConnection>>() {
      @Override
      @NonNull
      public List<PhysicianConnection> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPhysicianEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "physicianEmail");
          final int _cursorIndexOfPatientUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "patientUsername");
          final int _cursorIndexOfPhysicianName = CursorUtil.getColumnIndexOrThrow(_cursor, "physicianName");
          final int _cursorIndexOfConnectionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "connectionDate");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<PhysicianConnection> _result = new ArrayList<PhysicianConnection>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhysicianConnection _item;
            final String _tmpPhysicianEmail;
            _tmpPhysicianEmail = _cursor.getString(_cursorIndexOfPhysicianEmail);
            final String _tmpPatientUsername;
            _tmpPatientUsername = _cursor.getString(_cursorIndexOfPatientUsername);
            final String _tmpPhysicianName;
            _tmpPhysicianName = _cursor.getString(_cursorIndexOfPhysicianName);
            final long _tmpConnectionDate;
            _tmpConnectionDate = _cursor.getLong(_cursorIndexOfConnectionDate);
            final ConnectionStatus _tmpStatus;
            _tmpStatus = __ConnectionStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new PhysicianConnection(_tmpPhysicianEmail,_tmpPatientUsername,_tmpPhysicianName,_tmpConnectionDate,_tmpStatus,_tmpIsSynced);
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

  private String __ConnectionStatus_enumToString(@NonNull final ConnectionStatus _value) {
    switch (_value) {
      case PENDING: return "PENDING";
      case ACCEPTED: return "ACCEPTED";
      case REJECTED: return "REJECTED";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private ConnectionStatus __ConnectionStatus_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "PENDING": return ConnectionStatus.PENDING;
      case "ACCEPTED": return ConnectionStatus.ACCEPTED;
      case "REJECTED": return ConnectionStatus.REJECTED;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
