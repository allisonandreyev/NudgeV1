package com.nudge.app.ui;

import com.nudge.app.data.DataPointDao;
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
public final class TrainingViewModel_Factory implements Factory<TrainingViewModel> {
  private final Provider<DataPointDao> dataPointDaoProvider;

  public TrainingViewModel_Factory(Provider<DataPointDao> dataPointDaoProvider) {
    this.dataPointDaoProvider = dataPointDaoProvider;
  }

  @Override
  public TrainingViewModel get() {
    return newInstance(dataPointDaoProvider.get());
  }

  public static TrainingViewModel_Factory create(Provider<DataPointDao> dataPointDaoProvider) {
    return new TrainingViewModel_Factory(dataPointDaoProvider);
  }

  public static TrainingViewModel newInstance(DataPointDao dataPointDao) {
    return new TrainingViewModel(dataPointDao);
  }
}
