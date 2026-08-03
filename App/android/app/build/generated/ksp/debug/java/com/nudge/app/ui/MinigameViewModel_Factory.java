package com.nudge.app.ui;

import com.nudge.app.data.UserStatsDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class MinigameViewModel_Factory implements Factory<MinigameViewModel> {
  private final Provider<UserStatsDao> userStatsDaoProvider;

  public MinigameViewModel_Factory(Provider<UserStatsDao> userStatsDaoProvider) {
    this.userStatsDaoProvider = userStatsDaoProvider;
  }

  @Override
  public MinigameViewModel get() {
    return newInstance(userStatsDaoProvider.get());
  }

  public static MinigameViewModel_Factory create(Provider<UserStatsDao> userStatsDaoProvider) {
    return new MinigameViewModel_Factory(userStatsDaoProvider);
  }

  public static MinigameViewModel newInstance(UserStatsDao userStatsDao) {
    return new MinigameViewModel(userStatsDao);
  }
}
