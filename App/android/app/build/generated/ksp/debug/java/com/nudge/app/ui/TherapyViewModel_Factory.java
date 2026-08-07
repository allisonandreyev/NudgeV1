package com.nudge.app.ui;

import com.nudge.app.data.TherapySessionDao;
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
public final class TherapyViewModel_Factory implements Factory<TherapyViewModel> {
  private final Provider<TherapySessionDao> therapySessionDaoProvider;

  public TherapyViewModel_Factory(Provider<TherapySessionDao> therapySessionDaoProvider) {
    this.therapySessionDaoProvider = therapySessionDaoProvider;
  }

  @Override
  public TherapyViewModel get() {
    return newInstance(therapySessionDaoProvider.get());
  }

  public static TherapyViewModel_Factory create(
      Provider<TherapySessionDao> therapySessionDaoProvider) {
    return new TherapyViewModel_Factory(therapySessionDaoProvider);
  }

  public static TherapyViewModel newInstance(TherapySessionDao therapySessionDao) {
    return new TherapyViewModel(therapySessionDao);
  }
}
