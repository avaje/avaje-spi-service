

module io.avaje.spi.blackbox {
  requires io.avaje.inject;
  requires static io.avaje.spi;
  requires java.compiler;

  provides io.avaje.spi.test.SPIInterface with io.avaje.spi.test.CommonClass,
  io.avaje.spi.test.CommonClass2,
  io.avaje.spi.test.CommonClass3,
  io.avaje.spi.test.CommonClass4.CommonClass4SPI,
  io.avaje.spi.test.CommonClass4.CommonClass5SPI,
  io.avaje.spi.test.CommonClass5,
  io.avaje.spi.test.ManualSPI,
  io.avaje.spi.test.SPIInterface.DefaultSPIInterface;

  exports io.avaje.spi.test;

  provides io.avaje.spi.test.SPIInterface.NestedSPIInterface with io.avaje.spi.test.CommonClass.CommonClassNested$SPIInterface, io.avaje.spi.test.CommonClass2, io.avaje.spi.test.SPIInterface.NestedSPIInterface.DefaultNested$$SPIInterface;

  provides io.avaje.inject.spi.InjectExtension with io.avaje.spi.test.InjectProvider;
 }
