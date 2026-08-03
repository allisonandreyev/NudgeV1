package com.nudge.app.ui;

import com.nudge.app.data.PhysicianConnectionDao;
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
public final class PhysicianViewModel_Factory implements Factory<PhysicianViewModel> {
  private final Provider<PhysicianConnectionDao> physicianConnectionDaoProvider;

  private final Provider<UserStatsDao> userStatsDaoProvider;

  public PhysicianViewModel_Factory(Provider<PhysicianConnectionDao> physicianConnectionDaoProvider,
      Provider<UserStatsDao> userStatsDaoProvider) {
    this.physicianConnectionDaoProvider = physicianConnectionDaoProvider;
    this.userStatsDaoProvider = userStatsDaoProvider;
  }

  @Override
  public PhysicianViewModel get() {
    return newInstance(physicianConnectionDaoProvider.get(), userStatsDaoProvider.get());
  }

  public static PhysicianViewModel_Factory create(
      Provider<PhysicianConnectionDao> physicianConnectionDaoProvider,
      Provider<UserStatsDao> userStatsDaoProvider) {
    return new PhysicianViewModel_Factory(physicianConnectionDaoProvider, userStatsDaoProvider);
  }

  public static PhysicianViewModel newInstance(PhysicianConnectionDao physicianConnectionDao,
      UserStatsDao userStatsDao) {
    return new PhysicianViewModel(physicianConnectionDao, userStatsDao);
  }
}
