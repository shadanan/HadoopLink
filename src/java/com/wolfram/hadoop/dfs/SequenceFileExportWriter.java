package com.wolfram.hadoop.dfs;


import com.wolfram.jlink.Expr;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.typedbytes.TypedBytesWritable;

import com.wolfram.hadoop.ExprUtil;

/**
 * Helper class to speed up export of Mathematica expressions to sequence files.
 */
public class SequenceFileExportWriter {

  SequenceFile.Writer writer;
  TypedBytesWritable key;
  TypedBytesWritable value;

  public SequenceFileExportWriter(Configuration conf, Path path)
      throws Exception {
    FileSystem fs = FileSystem.get(conf);
    key = new TypedBytesWritable();
    value = new TypedBytesWritable();
    writer = SequenceFile.createWriter(fs, conf, path, TypedBytesWritable.class,
        TypedBytesWritable.class);
  }

  public void write(Expr[][] list) throws Exception {
    for (int i = 0; i < list.length; i++) {
      if (list[i].length != 2) {
        throw new IllegalArgumentException("Malformed record");
      }
      key.setValue(ExprUtil.fromExpr(list[i][0]));
      value.setValue(ExprUtil.fromExpr(list[i][1]));
      writer.append(key, value);
    }
  }

  public void close() throws Exception {
    writer.close();
  }
}