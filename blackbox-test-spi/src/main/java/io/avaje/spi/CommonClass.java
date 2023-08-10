package io.avaje.spi;

@ServiceProvider
public class CommonClass implements SPIInterface {

  public void common() {
    System.out.println("some string idk");
  }
}
