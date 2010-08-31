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

  private HadoopLink link;

  @Override
  public void setup(Context context) {
    /* Initialize a Mathematica kernel */
    try {
      link = new HadoopLink(context.getConfiguration());
    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Could not start a Mathematica kernel");
    }
  }

  @Override
  public void map(TypedBytesWritable key, TypedBytesWritable value,
                  Context context) throws IOException, InterruptedException {
    // transform TypedBytesWritable to JLink Expr objects
    // submit 
  }

  @Override
  public void cleanup(Context context) {
    /* Shut down Mathematica connection */
    link.close();
  }

}
