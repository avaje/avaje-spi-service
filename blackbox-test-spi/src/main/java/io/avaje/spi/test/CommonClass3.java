package io.avaje.spi.test;

import io.avaje.spi.ServiceProvider;

@ServiceProvider
public class CommonClass3 extends AbstractSPIExtension {

  @Override
  public void doSomething() {
    System.out.println("some string idk");
  }
}
