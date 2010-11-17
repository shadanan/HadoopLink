package com.wolfram.hadoop;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

/**
 * Wrapper around a KernelLink, specialized for use from a Hadoop job.
 */
public class MapReduceKernelLink {
  static final Log LOG = LogFactory.getLog(MapReduceKernelLink.class);

  public static final String JLINK_PATH_KEY = "wolfram.jlink.path";
  public static final String MATH_ARGS_KEY = "wolfram.math.args";

  public static KernelLink get(Configuration conf) throws MathLinkException {
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
      KernelLink link = MathLinkFactory.createKernelLink(mathArgs);
      link.discardAnswer();

      link.enableObjectReferences();

      /* Load the map-reduce API code */
      ClassLoader cl = getClass().getClassLoader();
      InputStream stream = cl.getResourceAsStream("MapReduceAPI.m");
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
      StringBuilder sb = new StringBuilder();
      try {
        String line;
        while((line = reader.readLine()) != null) {
          sb.append(line);
        }
      } catch (IOException ex) {
        throw new RuntimeError("Couldn't load map-reduce API");
      }
      link.evaluate(sb.toString());
      link.discardAnswer();

      /* Register a shutdown hook to close this kernel */
      Runtime.getRuntime().addShutdownHook(new ShutdownHook(link));

      return link;
  }
}

class ShutdownHook extends Thread {

  private KernelLink link;

  ShutdownHook(KernelLink link) {
    this.link = link;
  }

  public void run() {
    link.terminateKernel();
    link.close();
  }
}
