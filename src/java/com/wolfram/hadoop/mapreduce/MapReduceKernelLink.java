package com.wolfram.hadoop.mapreduce;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.StringUtils;

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
   * @throws IOException 
   * Create a KernelLink object initialized for use in a map or reduce task.
   * @throws  
   */
  public static KernelLink get(Configuration conf) throws MathLinkException, IOException {
    String mathFile = findMathOnPath();
    String defaultMathArgs = null;
    if (mathFile != null) {
      defaultMathArgs = "-linkmode launch -linkname " + mathFile + " -mathlink";
    }
    String defaultJLinkPath = getJLinkPath(mathFile);
    
    /* Find and set the location of JLink.jar */
    String jLinkPath = conf.get(JLINK_PATH_KEY, defaultJLinkPath);
    if (jLinkPath == null) {
      throw new RuntimeException("wolfram.jlink.path must be defined");
    }
    System.setProperty("com.wolfram.jlink.libdir", jLinkPath);

    /* Find and set the arguments to use when starting a kernel */
    String mathArgs = conf.get(MATH_ARGS_KEY, defaultMathArgs);
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
  
  public static String findMathOnPath() {
    Map<String, String> env = System.getenv();
    String[] paths = (env.get("PATH") + ":/usr/local/bin").split(":");
    for (String path : paths) {
      if (path.length() == 0) continue;
      File mathFile = new File(path + File.separator + "math");
      if (mathFile.isFile() && mathFile.canExecute()) {
        return mathFile.getAbsolutePath();
      }
    }
    return null;
  }
  
  public static String getJLinkPath(String mathLink) throws IOException {
    if (mathLink == null) return null;
    
    File jLinkDir = null;
    File mathFile = new File(mathLink).getCanonicalFile();
    String jLinkPath = "SystemFiles" + File.separator + "Links" + File.separator + "JLink";
    
    jLinkDir = new File(mathFile.getParentFile().getParentFile(), jLinkPath);
    if (jLinkDir.isDirectory()) {
      return jLinkDir.getCanonicalPath();
    }
    
    jLinkDir = new File(mathFile.getParentFile().getParentFile().getParentFile(), jLinkPath);
    if (jLinkDir.isDirectory()) {
      return jLinkDir.getCanonicalPath();
    }
    
    return null;
  }
  
  public static void main(String args[]) throws MathLinkException, IOException {
    String mathFile = findMathOnPath();
    String jLinkPath = getJLinkPath(mathFile);
    
    System.out.println(mathFile);
    System.out.println(jLinkPath);
  }
}

class ShutdownHook extends Thread {

  private KernelLink link;

  ShutdownHook(KernelLink link) {
    this.link = link;
  }

  @Override
  public void run() {
    link.terminateKernel();
    link.close();
  }
}
