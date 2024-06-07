package io.avaje.spi.test;

import io.avaje.spi.Service;
import io.avaje.spi.ServiceProvider;

@Service
public interface SPIInterface {
  public interface NestedSPIInterface {
    @ServiceProvider
    public class DefaultNested$$SPIInterface implements NestedSPIInterface {}
  }

  @ServiceProvider
  public class DefaultSPIInterface implements SPIInterface {}
}
