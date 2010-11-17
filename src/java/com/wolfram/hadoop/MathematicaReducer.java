package com.wolfram.hadoop;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.typedbytes.TypedBytesWritable;
import org.apache.hadoop.util.StringUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

public class MathematicaReducer extends
    Reducer<TypedBytesWritable, TypedBytesWritable,
    TypedBytesWritable, TypedBytesWritable> {
  private static final Log LOG = LogFactory.getLog(MathematicaReducer.class);

  private KernelLink link;

  @Override
  public void setup(Context context) {

    /* Initialize a Mathematica kernel */
    try {
      Configuration conf = context.getConfiguration();
      link = MapReduceKernelLink.get(conf);


    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error initializing kernel for task");
    }
  }

  @Override
  public void reduce(TypedBytesWritable key,
                     Iterable<TypedBytesWritable> values,
                     Context context)
      throws IOException, InterruptedException {

  }

  @Override
  public void cleanup(Context context) {
    /* Shut down Mathematica connection */
    link.terminateKernel();
    link.close();
  }

}
