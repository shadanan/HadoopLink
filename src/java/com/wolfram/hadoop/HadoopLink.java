package com.wolfram.hadoop;

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

/**
 * Wrapper around a KernelLink, specialized for use from a Hadoop job.
 */
public class HadoopLink {

  private static final Expr MR_TAG = new Expr(Expr.SYMBOL, "$mapreduce");
  private static final Expr MR_FUNCTION = new Expr(Expr.SYMBOL, "MapReduceFunction");

  public static final String JLINK_PATH_KEY = "wolfram.jlink.path";
  public static final String MATH_ARGS_KEY = "wolfram.math.args";

  private KernelLink link;

  private List<Expr> keys;
  private List<Expr> values;

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
    /* Set up key/value queues for returning results */
    keys = new ArrayList<Expr>();
    values = new ArrayList<Expr>();
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

  public void evaluateToString(String s) throws MathLinkException {
    System.out.println(link.evaluateToOutputForm(s, 78));
  }

  /**
   * Evaluate a key-value pair.
   *
   * @param key   Key to proces.
   * @param value Value to process.
   * @throws MathLinkException
   */
  public void evaluateKeyValuePair(Expr key, Expr value)
      throws MathLinkException {
    /* Function call for evaluating a key-value pair */
    Expr func = new Expr(MR_FUNCTION,
                         new Expr[] {key, value});
    /* Collect the results from the evaluation with Reap */
    Expr reap = new Expr(new Expr(Expr.SYMBOL, "Reap"),
                         new Expr[] {func, MR_TAG});
    Expr last = new Expr(new Expr(Expr.SYMBOL, "Last"),
                         new Expr[] {reap});
    Expr flat = new Expr(new Expr(Expr.SYMBOL, "Flatten"),
                         new Expr[] {last, new Expr(1)});
    link.evaluate(flat);
    link.waitForAnswer();
    Expr answer = link.getExpr();
    keys.clear();
    values.clear();
    /* The return type must be a list of pairs */
    if (!answer.matrixQ()) return;
    int[] dimensions = answer.dimensions();
    int n = dimensions[0];
    int[] partSpec = new int[2];
    for (int i = 1; i <= n; i++) {
      /* Each response record should be a key, value pair */
      if (dimensions[i] != 2) continue;
      partSpec[0] = i;
      partSpec[1] = 1; // get the key
      keys.add(answer.part(partSpec));
      partSpec[1] = 2; // get the value
      values.add(answer.part(partSpec));
    }
  }

  /**
   * Pull the next key off the queue
   *
   * @return Expr containing the next key from the last evaluation.
   */
  public Expr nextKey() {
    if (keys.isEmpty()) {
      return null;
    }
    return keys.remove(0);
  }

  /**
   * Pull the next value off the queue.
   *
   * @return  Expr containing the next value from the last evaluation.
   */
  public Expr nextValue() {
    if (values.isEmpty()) {
      return null;
    }
    return values.remove(0);
  }

  /**
   * Shutdown the Mathematica kernel.
   */
  public void close() {
    link.terminateKernel();
    link.close();
  }
}
