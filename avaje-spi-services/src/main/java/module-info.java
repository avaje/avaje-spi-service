module io.avaje.spi {

  exports io.avaje.spi;
  requires java.compiler;
  requires static io.avaje.prism;
  provides javax.annotation.processing.Processor with io.avaje.spi.internal.ServiceProcessor;

}
