package com.nudge.app.bluetooth;

import android.content.Context;
import com.nudge.app.data.DataPointDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class BluetoothViewModel_Factory implements Factory<BluetoothViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<DataPointDao> dataPointDaoProvider;

  public BluetoothViewModel_Factory(Provider<Context> contextProvider,
      Provider<DataPointDao> dataPointDaoProvider) {
    this.contextProvider = contextProvider;
    this.dataPointDaoProvider = dataPointDaoProvider;
  }

  @Override
  public BluetoothViewModel get() {
    return newInstance(contextProvider.get(), dataPointDaoProvider.get());
  }

  public static BluetoothViewModel_Factory create(Provider<Context> contextProvider,
      Provider<DataPointDao> dataPointDaoProvider) {
    return new BluetoothViewModel_Factory(contextProvider, dataPointDaoProvider);
  }

  public static BluetoothViewModel newInstance(Context context, DataPointDao dataPointDao) {
    return new BluetoothViewModel(context, dataPointDao);
  }
}
