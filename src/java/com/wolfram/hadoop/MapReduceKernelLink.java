package com.wolfram.hadoop;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;
import org.apache.hadoop.util.StringUtils;

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

  /**
   * Create a KernelLink object initialized for use in a map or reduce task.
   */
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
      loadPackageFromJar(link, "MapReduceAPI.m");

      /* Register a shutdown hook to close this kernel */
      Runtime.getRuntime().addShutdownHook(new ShutdownHook(link));

      return link;
  }

  /**
   * Attempt to load a Mathematica library present as a resource in the
   * parent jar file.
   *
   * @param link Mathematica kernel where the package will be loaded
   * @param packageName name of the file in the jar to load
   */
  public static void loadPackageFromJar(KernelLink link, String packageName) {
    ClassLoader loader = ClassLoader.getSystemClassLoader();
    URL packageURL = loader.getResource(packageName);
    if (packageURL == null) {
      LOG.error("Could not load "+packageName);
      return;
    }
    String packagePath = packageURL.getPath();
    int n = packagePath.lastIndexOf("!");
    String jarPath = packagePath.substring(5, n);
    LOG.info("Loading "+packageName+" from "+jarPath);
    try {
      link.putFunction("Import", 2);
        link.put(jarPath);
        link.putFunction("List", 4);
          link.put("ZIP");
          link.put("FileNames");
          link.put(packageName);
          link.put("Package");
      link.endPacket();
      link.discardAnswer();
    } catch (MathLinkException ex) {
      LOG.error(StringUtils.stringifyException(ex));
    }
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
