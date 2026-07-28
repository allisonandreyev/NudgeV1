package com.nudge.app.bluetooth;

import android.content.Context;
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

  public BluetoothViewModel_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BluetoothViewModel get() {
    return newInstance(contextProvider.get());
  }

  public static BluetoothViewModel_Factory create(Provider<Context> contextProvider) {
    return new BluetoothViewModel_Factory(contextProvider);
  }

  public static BluetoothViewModel newInstance(Context context) {
    return new BluetoothViewModel(context);
  }
}
