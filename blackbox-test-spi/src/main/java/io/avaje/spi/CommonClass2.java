package io.avaje.spi;

import io.avaje.spi.SPIInterface.NestedSPIInterface;

@ServiceProvider({SPIInterface.class, NestedSPIInterface.class})
public class CommonClass2 implements SPIInterface, NestedSPIInterface {

  public void common() {
    System.out.println("some string idk");
  }
}
