package com.wolfram.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.StringUtils;

import org.junit.*;
import static org.junit.Assert.*;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.MathLinkException;

public class TestHadoopLink {

  Configuration conf;

  @Before
  public void setUp() {
    conf = new Configuration();
    conf.set(HadoopLink.JLINK_PATH_KEY, "/Applications/Mathematica.app/SystemFiles/Links/JLink");
    conf.set(HadoopLink.MATH_ARGS_KEY, "-linkmode launch -linkname /Applications/Mathematica.app/Contents/MacOS/MathKernel -mathlink");
  }

  @Test
  public void testLaunchKernel() {
    HadoopLink link = null;
    try {
      link = new HadoopLink(conf);
    } catch (MathLinkException ex) {
      fail();
    } finally {
      if (link != null) { link.close(); }
    }
  }

  @Test
  public void testDefineEvaluationFunction() {
    HadoopLink link = null;
    try {
      link = new HadoopLink(conf);
      Expr args = new Expr(ExprUtil.toSymbol("List"),
          new Expr[] {ExprUtil.toSymbol("k"),
                      ExprUtil.toSymbol("v")});
      Expr fn = new Expr(ExprUtil.toSymbol("Function"),
          new Expr[] {args,
                      ExprUtil.toSymbol("k")});
      link.defineEvaluationFunction(fn);
    } catch (MathLinkException ex) {
      System.err.println(StringUtils.stringifyException(ex));
      fail();
    } finally {
      if (link != null) { link.close(); }
    }
  }
}
