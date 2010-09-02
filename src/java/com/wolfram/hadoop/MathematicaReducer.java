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

  private HadoopLink link;

  private TypedBytesWritable outputKey;
  private TypedBytesWritable outputValue;

  @Override
  public void setup(Context context) {
    /* Initialize a Mathematica kernel */
    try {
      Configuration conf = context.getConfiguration();
      link = new HadoopLink(conf);
      /* Load any .m files supplied with this job */
      String packageList = conf.get(MathematicaJob.M_PACKAGES);
      for (String packagefile : packageList.split(",")) {
        link.load(context, packagefile);
      }
      /* Set up the evaluation function for this task */
      Expr reducer = link.load(context, conf.get(REDUCER));
      link.defineEvaluationFunction(reducer);
    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error initializing kernel for task");
    } catch (IOException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error reading library file");
    }
  }

  @Override
  public void reduce(TypedBytesWritable key,
                     Iterable<TypedBytesWritable> values,
                     Context context)
      throws IOException, InterruptedException {
    Expr k = ExprUtil.toExpr(key.getValue());
    /* TODO replace with some mechanism for passing an iterator to M-- */
    Expr vals = new Expr(ExprUtil.toSymbol("List"), new Expr[] {});
    for (TypedBytesWritable value : values) {
      Expr v = ExprUtil.toExpr(value.getValue());
      vals = vals.insert(v, -1); // append to the end of the value queue
    }
    try {
      link.evaluateKeyValuePair(k, vals);
    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      return;
    }
    Expr resultKey;
    Expr resultValue;
    while((resultKey = link.nextKey()) != null) {
      resultValue = link.nextValue();
      outputKey.setValue(ExprUtil.fromExpr(resultKey));
      outputValue.setValue(ExprUtil.fromExpr(resultValue));
      context.write(outputKey, outputValue);
    }
  }

  @Override
  public void cleanup(Context context) {
    /* Shut down Mathematica connection */
    link.close();
  }

}
