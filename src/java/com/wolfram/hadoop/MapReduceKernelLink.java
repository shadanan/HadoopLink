package com.wolfram.hadoop;

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

  private static final Expr MR_FUNCTION = ExprUtil.toSymbol("MapReduceFunction");

  public static final String JLINK_PATH_KEY = "wolfram.jlink.path";
  public static final String MATH_ARGS_KEY = "wolfram.math.args";

  private Configuration conf;
  private KernelLink link;

  public MapReduceKernelLink(Configuration conf) throws MathLinkException {
    this.conf = conf;

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

    /* Register a shutdown hook to close this kernel */
    Runtime.getRuntime().addShutdownHook(new ShutdownHook(this));
  }

  /**
   * Evaluate a Mathematica .m file in this kernel.
   *
   * @param context   The task context
   * @param filename  The name of the file to evaluate
   * @return Expr
   * @throws IOException
   */
  @SuppressWarnings("rawtypes")
  public Expr load(TaskInputOutputContext context, String filename)
      throws IOException, MathLinkException {
    FileSystem fs = FileSystem.get(conf);
    Path path = context.getWorkingDirectory();
    Path file = new Path(path, filename);
    if (!fs.exists(file)) {
      String error = String.format("%s not found", filename);
      throw new IOException(error);
    }
    FSDataInputStream f = fs.open(file);
    StringBuffer sb = new StringBuffer();
    byte[] buffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = f.read(0, buffer, 0, 1024)) > 0) {
      byte[] chunk = new byte[bytesRead];
      System.arraycopy(buffer, 0, chunk, 0, bytesRead);
      sb.append(chunk);
    }
    f.close();
    link.evaluate(sb.toString());
    link.waitForAnswer();
    return link.getExpr();
  }

  public void defineEvaluationFunction(String functionDefinition)
      throws MathLinkException {
    link.evaluate(functionDefinition);
    link.waitForAnswer();
    Expr function = link.getExpr();
    defineEvaluationFunction(function);
  }

  public void defineEvaluationFunction(Expr function)
      throws MathLinkException {
    Expr assign = new Expr(new Expr(Expr.SYMBOL, "Set"),
                           new Expr[] {MR_FUNCTION, function});
    link.evaluate(assign);
    link.discardAnswer();
  }

  /**
   * Evaluate the supplied string, discarding the results.
   * 
   * @param s String to evaluate.
   */
  public void evaluate(String s) throws MathLinkException {
    link.evaluate(s);
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

class ShutdownHook extends Thread {

  private MapReduceKernelLink link;

  ShutdownHook(MapReduceKernelLink link) {
    this.link = link;
  }

  public void run() {
    link.close();
  }
}
