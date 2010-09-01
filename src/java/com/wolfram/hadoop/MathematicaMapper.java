package com.wolfram.hadoop;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.typedbytes.TypedBytesWritable;
import org.apache.hadoop.util.StringUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.MathLinkException;

public class MathematicaMapper
    extends Mapper<TypedBytesWritable, TypedBytesWritable,
                   TypedBytesWritable, TypedBytesWritable> {
  private static final Log LOG = LogFactory.getLog(MathematicaMapper.class);

  public static final String MAPPER = "wolfram.mapper.file"; 

  private HadoopLink link;

  private TypedBytesWritable outputKey;
  private TypedBytesWritable outputValue;

  @Override
  public void setup(Context context) {
    /* Initialize a Mathematica kernel */
    try {
      Configuration conf = context.getConfiguration();
      link = new HadoopLink(conf);
      /* Set up the evaluation function for this task */
      Expr mapper = link.load(context, conf.get(MAPPER));
      link.defineEvaluationFunction(mapper);
    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error initializing kernel for mapper");
    } catch (IOException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error reading library file");
    }
  }

  @Override
  public void map(TypedBytesWritable key, TypedBytesWritable value,
                  Context context) throws IOException, InterruptedException {
    Expr k = ExprUtil.toExpr(key);
    Expr v = ExprUtil.toExpr(value);
    try {
      link.evaluateKeyValuePair(k, v);
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
