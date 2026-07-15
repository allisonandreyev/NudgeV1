package com.nudge.app;

import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.internal.GeneratedEntryPoint;

@OriginatingElement(
    topLevelClass = NudgeApplication.class
)
@GeneratedEntryPoint
@InstallIn(SingletonComponent.class)
public interface NudgeApplication_GeneratedInjector {
  void injectNudgeApplication(NudgeApplication nudgeApplication);
}
