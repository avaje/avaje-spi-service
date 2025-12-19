package io.avaje.spi.test;

import io.avaje.spi.ServiceProvider;

public class CommonClass4 extends AbstractSPIExtension {

  @Override
  public void doSomething() {
    System.out.println("some string idk");
  }

  @ServiceProvider
  public interface CommonClass4SPI {
    static CommonClass4 provider() {
      return new CommonClass4();
    }
  }

  @ServiceProvider
  public static class CommonClass5SPI {
    public static AbstractSPIExtension provider() {
      return new CommonClass4();
    }
  }
}
