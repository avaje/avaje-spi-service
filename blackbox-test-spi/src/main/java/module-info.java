import io.avaje.spi.test.SPIInterface;
module io.avaje.spi.blackbox {
  requires io.avaje.spi;
  requires java.compiler;
  provides SPIInterface with
  		io.avaje.spi.test.CommonClass,

  		io.avaje.spi.test.CommonClass2;
  exports io.avaje.spi.test;

  provides io.avaje.spi.test.SPIInterface.NestedSPIInterface with io.avaje.spi.test.CommonClass2;
 }
