package com.wolfram.hadoop;

import org.apache.hadoop.conf.Configuration;

import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

/**
 * Wrapper around a KernelLink, specialized for use from a Hadoop job.
 */
public class HadoopLink {

  private static final String JLINK_PATH_KEY = "wolfram.jlink.path";
  private static final String MATH_ARGS_KEY = "wolfram.math.args";

  private KernelLink link;

  public HadoopLink(Configuration conf) throws MathLinkException {
    /* Find and set the location of JLink.jar */
    String jlinkPath = conf.get(JLINK_PATH_KEY);
    if (jlinkPath == null) {
      throw new RuntimeException("wolfram.jlink.path must be defined");
    }
    System.setProperty("com.wolfram.jlink.libdir", jlinkPath);
    /* Find and set the arguments to use when starting a kernel */
    String mathArgs = conf.get(MATH_ARGS_KEY);
    if (mathArgs == null) {
      throw new RuntimeException("wolfram.math.args must be defined");
    }
    /* Attempt to obtain a connection to a kernel */
    link = MathLinkFactory.createKernelLink(mathArgs);
    link.discardAnswer();
  }

  /**
   * Shutdown the Mathematica kernel.
   */
  public void close() {
    link.terminateKernel();
    link.close();
  }
}
