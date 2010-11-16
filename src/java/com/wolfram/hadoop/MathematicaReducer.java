package com.wolfram.hadoop;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.typedbytes.TypedBytesWritable;
import org.apache.hadoop.util.StringUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.MathLinkException;

public class MathematicaReducer extends
    Reducer<TypedBytesWritable, TypedBytesWritable,
    TypedBytesWritable, TypedBytesWritable> {
  private static final Log LOG = LogFactory.getLog(MathematicaReducer.class);

  private MapReduceKernelLink link;

  private TypedBytesWritable outputKey;
  private TypedBytesWritable outputValue;

  @Override
  public void setup(Context context) {
    outputKey = new TypedBytesWritable();
    outputValue = new TypedBytesWritable();
    /* Initialize a Mathematica kernel */
    try {
      Configuration conf = context.getConfiguration();
      link = new MapReduceKernelLink(conf);
      /* Set up the evaluation function for this task */
      link.defineEvaluationFunction(conf.get(MathematicaJob.REDUCER));
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
    link.close();
  }

}
