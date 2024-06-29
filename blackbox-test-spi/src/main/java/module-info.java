import io.avaje.spi.test.SPIInterface;
import io.avaje.spi.test.CommonClass2;

module io.avaje.spi.blackbox {
  requires io.avaje.inject;
  requires static
  io.avaje.spi;
  requires java.compiler;
  provides SPIInterface with
  		io.avaje.spi.test. CommonClass,

  		io.avaje.spi. test.CommonClass2,
  		io.avaje.spi. test.CommonClass3
  		, io . avaje . spi . test . ManualSPI,
  		 io.avaje.spi.test.SPIInterface.DefaultSPIInterface
       ;
  exports io.avaje.spi.test;

  provides io.avaje.spi.test.SPIInterface.NestedSPIInterface with io.avaje.spi.test.CommonClass.CommonClassNested$SPIInterface, io.avaje.spi.test.CommonClass2, io.avaje.spi.test.SPIInterface.NestedSPIInterface.DefaultNested$$SPIInterface;

  provides io.avaje.inject.spi.InjectExtension with io.avaje.spi.test.InjectProvider;
 }
