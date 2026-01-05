import io.avaje.spi.test.*;

module io.avaje.spi.blackbox {
  requires io.avaje.inject;
  requires static io.avaje.spi;
  requires java.compiler;

  provides SPIInterface with
      CommonClass,
      CommonClass2,
      CommonClass3,
      CommonClass4.CommonClass4SPI,
      CommonClass4.CommonClass5SPI,
      CommonClass5,
      ManualSPI,
      SPIInterface.DefaultSPIInterface;

  exports io.avaje.spi.test;

  provides SPIInterface.NestedSPIInterface with
      CommonClass.CommonClassNested$SPIInterface,
      CommonClass2,
      SPIInterface.NestedSPIInterface.DefaultNested$$SPIInterface;
  provides io.avaje.inject.spi.InjectExtension with
      InjectProvider;

  uses CommonClass;
  uses SPIInterface;
  uses SPIInterface.NestedSPIInterface;
  uses io.avaje.inject.spi.InjectExtension;
}
