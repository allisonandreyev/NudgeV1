package com.nudge.app.ui;

import com.nudge.app.data.UserDao;
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

  public AuthViewModel_Factory(Provider<UserDao> userDaoProvider) {
    this.userDaoProvider = userDaoProvider;
  }

  @Override
  public AuthViewModel get() {
    return newInstance(userDaoProvider.get());
  }

  public static AuthViewModel_Factory create(Provider<UserDao> userDaoProvider) {
    return new AuthViewModel_Factory(userDaoProvider);
  }

  public static AuthViewModel newInstance(UserDao userDao) {
    return new AuthViewModel(userDao);
  }
}
