package com.nudge.app.di;

import com.nudge.app.data.NudgeDatabase;
import com.nudge.app.data.UserStatsDao;
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
public final class DatabaseModule_ProvideUserStatsDaoFactory implements Factory<UserStatsDao> {
  private final Provider<NudgeDatabase> databaseProvider;

  public DatabaseModule_ProvideUserStatsDaoFactory(Provider<NudgeDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public UserStatsDao get() {
    return provideUserStatsDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideUserStatsDaoFactory create(
      Provider<NudgeDatabase> databaseProvider) {
    return new DatabaseModule_ProvideUserStatsDaoFactory(databaseProvider);
  }

  public static UserStatsDao provideUserStatsDao(NudgeDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideUserStatsDao(database));
  }
}
