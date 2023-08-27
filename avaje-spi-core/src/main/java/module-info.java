module io.avaje.spi.core {

  exports io.avaje.spi.internal to io.avaje.spi.blackbox;
  requires java.compiler;
  requires static io.avaje.prism;

  provides javax.annotation.processing.Processor with io.avaje.spi.internal.ServiceProcessor;
}
