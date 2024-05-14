package io.avaje.spi.test;

import io.avaje.spi.Service;

@Service
public interface SPIInterface {
  public interface NestedSPIInterface {}
}
