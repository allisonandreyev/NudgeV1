package com.nudge.app.ui;

import com.nudge.app.data.DataPointDao;
import com.nudge.app.data.PhysicianConnectionDao;
import com.nudge.app.data.TherapySessionDao;
import com.nudge.app.data.UserDao;
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
public final class AuthViewModel_Factory implements Factory<AuthViewModel> {
  private final Provider<UserDao> userDaoProvider;

  private final Provider<DataPointDao> dataPointDaoProvider;

  private final Provider<UserStatsDao> userStatsDaoProvider;

  private final Provider<PhysicianConnectionDao> physicianConnectionDaoProvider;

  private final Provider<TherapySessionDao> therapySessionDaoProvider;

  public AuthViewModel_Factory(Provider<UserDao> userDaoProvider,
      Provider<DataPointDao> dataPointDaoProvider, Provider<UserStatsDao> userStatsDaoProvider,
      Provider<PhysicianConnectionDao> physicianConnectionDaoProvider,
      Provider<TherapySessionDao> therapySessionDaoProvider) {
    this.userDaoProvider = userDaoProvider;
    this.dataPointDaoProvider = dataPointDaoProvider;
    this.userStatsDaoProvider = userStatsDaoProvider;
    this.physicianConnectionDaoProvider = physicianConnectionDaoProvider;
    this.therapySessionDaoProvider = therapySessionDaoProvider;
  }

  @Override
  public AuthViewModel get() {
    return newInstance(userDaoProvider.get(), dataPointDaoProvider.get(), userStatsDaoProvider.get(), physicianConnectionDaoProvider.get(), therapySessionDaoProvider.get());
  }

  public static AuthViewModel_Factory create(Provider<UserDao> userDaoProvider,
      Provider<DataPointDao> dataPointDaoProvider, Provider<UserStatsDao> userStatsDaoProvider,
      Provider<PhysicianConnectionDao> physicianConnectionDaoProvider,
      Provider<TherapySessionDao> therapySessionDaoProvider) {
    return new AuthViewModel_Factory(userDaoProvider, dataPointDaoProvider, userStatsDaoProvider, physicianConnectionDaoProvider, therapySessionDaoProvider);
  }

  public static AuthViewModel newInstance(UserDao userDao, DataPointDao dataPointDao,
      UserStatsDao userStatsDao, PhysicianConnectionDao physicianConnectionDao,
      TherapySessionDao therapySessionDao) {
    return new AuthViewModel(userDao, dataPointDao, userStatsDao, physicianConnectionDao, therapySessionDao);
  }
}
