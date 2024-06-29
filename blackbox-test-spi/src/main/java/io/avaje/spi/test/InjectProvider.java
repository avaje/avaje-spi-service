package io.avaje.spi.test;

import java.lang.reflect.Type;

import io.avaje.inject.BeanScopeBuilder;
import io.avaje.inject.spi.InjectPlugin;
import io.avaje.spi.ServiceProvider;

/** Plugin for avaje inject that provides a default Jsonb instance. */
@ServiceProvider
public final class InjectProvider implements InjectPlugin {

  @Override
  public Type[] provides() {
    return null;
  }

  @Override
  public void apply(BeanScopeBuilder builder) {}
}
