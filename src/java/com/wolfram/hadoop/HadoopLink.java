package com.wolfram.hadoop;

import org.apache.hadoop.conf.Configuration;

import com.wolfram.jlink.KernelLink;

/**
 * Wrapper around a KernelLink, specialized for use from a Hadoop job.
 */
public class HadoopLink {

  private static final String JLINK_PATH_KEY = "wolfram.jlink.path";
  private static final String MATH_ARGS_KEY = "wolfram.math.args";

  private KernelLink link;

  public HadoopLink(Configuration conf) {
   
  }
}
