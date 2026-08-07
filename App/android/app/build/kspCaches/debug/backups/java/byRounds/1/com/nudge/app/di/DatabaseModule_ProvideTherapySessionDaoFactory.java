package com.nudge.app.di;

import com.nudge.app.data.NudgeDatabase;
import com.nudge.app.data.TherapySessionDao;
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
public final class DatabaseModule_ProvideTherapySessionDaoFactory implements Factory<TherapySessionDao> {
  private final Provider<NudgeDatabase> databaseProvider;

  public DatabaseModule_ProvideTherapySessionDaoFactory(Provider<NudgeDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public TherapySessionDao get() {
    return provideTherapySessionDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideTherapySessionDaoFactory create(
      Provider<NudgeDatabase> databaseProvider) {
    return new DatabaseModule_ProvideTherapySessionDaoFactory(databaseProvider);
  }

  public static TherapySessionDao provideTherapySessionDao(NudgeDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideTherapySessionDao(database));
  }
}
