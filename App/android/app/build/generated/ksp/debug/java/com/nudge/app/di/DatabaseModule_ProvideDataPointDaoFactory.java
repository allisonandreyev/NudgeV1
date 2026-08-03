package com.nudge.app.di;

import com.nudge.app.data.DataPointDao;
import com.nudge.app.data.NudgeDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class DatabaseModule_ProvideDataPointDaoFactory implements Factory<DataPointDao> {
  private final Provider<NudgeDatabase> databaseProvider;

  public DatabaseModule_ProvideDataPointDaoFactory(Provider<NudgeDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public DataPointDao get() {
    return provideDataPointDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideDataPointDaoFactory create(
      Provider<NudgeDatabase> databaseProvider) {
    return new DatabaseModule_ProvideDataPointDaoFactory(databaseProvider);
  }

  public static DataPointDao provideDataPointDao(NudgeDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDataPointDao(database));
  }
}
