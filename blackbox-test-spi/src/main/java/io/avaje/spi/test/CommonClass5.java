package io.avaje.spi.test;

import io.avaje.spi.ServiceProvider;

@ServiceProvider
public class CommonClass5 extends AbstractSPIExtension {

  private CommonClass5() {
  }

  @Override
  public void doSomething() {
    System.out.println("some string idk");
  }

  public static CommonClass5 provider() {
    return new CommonClass5();
  }
}
