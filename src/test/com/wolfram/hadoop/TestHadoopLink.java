package com.wolfram.hadoop;

import org.apache.hadoop.conf.Configuration;

import org.junit.*;
import static org.junit.Assert.*;

import com.wolfram.jlink.MathLinkException;

public class TestHadoopLink {

  @Test
  public void testLaunchKernel() {
    Configuration conf = new Configuration();
    conf.set(HadoopLink.JLINK_PATH_KEY, "/Applications/Mathematica.app/SystemFiles/Links/JLink");
    conf.set(HadoopLink.MATH_ARGS_KEY, "-linkmode launch -linkname /Applications/Mathematica.app/Contents/MacOS/MathKernel -mathlink");
    HadoopLink link = null;
    try {
      link = new HadoopLink(conf);
    } catch (MathLinkException ex) {
      fail();
    } finally {
      if (link != null) { link.close(); }
    }
  }
}
