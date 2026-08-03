package com.nudge.app.di;

import com.nudge.app.data.NudgeDatabase;
import com.nudge.app.data.PhysicianConnectionDao;
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
public final class DatabaseModule_ProvidePhysicianConnectionDaoFactory implements Factory<PhysicianConnectionDao> {
  private final Provider<NudgeDatabase> databaseProvider;

  public DatabaseModule_ProvidePhysicianConnectionDaoFactory(
      Provider<NudgeDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public PhysicianConnectionDao get() {
    return providePhysicianConnectionDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvidePhysicianConnectionDaoFactory create(
      Provider<NudgeDatabase> databaseProvider) {
    return new DatabaseModule_ProvidePhysicianConnectionDaoFactory(databaseProvider);
  }

  public static PhysicianConnectionDao providePhysicianConnectionDao(NudgeDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.providePhysicianConnectionDao(database));
  }
}
