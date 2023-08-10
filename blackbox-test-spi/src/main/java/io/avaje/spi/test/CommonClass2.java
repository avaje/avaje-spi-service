package io.avaje.spi.test;

import io.avaje.spi.ServiceProvider;
import io.avaje.spi.test.SPIInterface.NestedSPIInterface;

@ServiceProvider({SPIInterface.class, NestedSPIInterface.class})
public class CommonClass2 implements SPIInterface, NestedSPIInterface {

  public void common() {
    System.out.println("some string idk");
  }
}
