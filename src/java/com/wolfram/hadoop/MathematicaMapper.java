package com.wolfram.hadoop;

import java.io.IOException;

import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.typedbytes.TypedBytesWritable;

public class MathematicaMapper
    extends Mapper<TypedBytesWritable, TypedBytesWritable,
                   TypedBytesWritable, TypedBytesWritable> {

  @Override
  public void setup(Context context) {
    // initialize a Mathematica kernel
  }
  
  @Override
  public void map(TypedBytesWritable key, TypedBytesWritable value,
                  Context context) throws IOException, InterruptedException {
    // transform TypedBytesWritable to JLink Expr objects
    // submit 
  }

  @Override
  public void cleanup(Context context) {
    // shut down kernel link
  }
  
}
