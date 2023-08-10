package io.avaje.spi.test;

import io.avaje.spi.ServiceProvider;

@ServiceProvider
public class CommonClass implements SPIInterface {

  public void common() {
    System.out.println("some string idk");
  }
}
