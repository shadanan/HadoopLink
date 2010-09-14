package com.wolfram.hadoop;

import java.util.ArrayList;

import com.wolfram.jlink.Expr;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.typedbytes.TypedBytesWritable;

/**
 * Helper class to speed up importing SequenceFiles into Mathematica.
 */
public class SequenceFileImportReader {

  SequenceFile.Reader reader;
  Class<?> keyClass;
  Class<?> valueClass;
  Writable key;
  Writable value;

  public SequenceFileImportReader(Configuration conf, Path sequenceFile)
      throws Exception {
    FileSystem fs = FileSystem.get(conf);
    reader = new SequenceFile.Reader(fs, sequenceFile, conf);
    keyClass = reader.getKeyClass();
    valueClass = reader.getValueClass();
    key = (Writable) ReflectionUtils.newInstance(keyClass, conf);
    value = (Writable) ReflectionUtils.newInstance(valueClass, conf);
  }

  private Expr writableToExpr(Writable w) {
    if (w instanceof TypedBytesWritable) {
      return ExprUtil.toExpr(((TypedBytesWritable) w).getValue());
    }
    return null;
  }

  public Expr next() throws Exception {
    Expr record;
    if (reader.next(key, value)) {
      record = new Expr(
          ExprUtil.toSymbol("List"),
          new Expr[] {
            writableToExpr(key),
            writableToExpr(value)
          });
      return record;
    } else {
      return null;
    }
  }

  public Object[] next(int maxRecords) throws Exception {
    ArrayList<Expr> records = new ArrayList<Expr>(maxRecords);
    for (int i = 0; i < maxRecords; i++) {
      Expr record = next();
      if (record == null) {
        if (i == 0) { return null; }
        break;
      }
      records.add(record);
    }
    return records.toArray();
  }

  public void close() throws Exception {
    reader.close();
  }
}
