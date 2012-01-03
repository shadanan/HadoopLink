package com.wolfram.hbase;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

public class HTable extends org.apache.hadoop.hbase.client.HTable {
  private class Column {
    byte[] family;
    byte[] qualifier;
    
    public Column(byte[] family, byte[] qualifier) {
      this.family = family;
      this.qualifier = qualifier;
    }
    
    @Override
    public int hashCode() {
      return Arrays.hashCode(family) + Arrays.hashCode(qualifier);
    }
    
    @Override
    public boolean equals(Object obj) {
      Column c = (Column)obj;
      return Arrays.equals(family, c.family) && Arrays.equals(qualifier, c.qualifier);
    }
  }
  
  private class Interval implements Comparable<Interval> {
    long start;
    long stop;
    
    public Interval(long start, long stop) {
      if (stop < start) {
        throw new RuntimeException("Invalid interval");
      }
      
      this.start = start;
      this.stop = stop;
    }
    
    @Override
    public int compareTo(Interval i) {
      return new Long(start).compareTo(i.start);
    }
    
    public boolean overlaps(Interval i) {
      return (i.start <= start && i.stop > start ||
          i.start < stop && i.stop >= stop);
    }
  }
  
  private Decoder keyColumn = null;
  private HashMap<Column, Decoder> defaultColumns = new HashMap<Column, Decoder>();
  private HashMap<Column, TreeMap<Interval, Decoder>> versionedColumns = new HashMap<Column, TreeMap<Interval, Decoder>>();
  
  public void setDecoder(Decoder decoder) {
    keyColumn = decoder;
  }
  
  public void setDecoder(byte[] family, byte[] qualifier, Decoder decoder) {
    defaultColumns.put(new Column(family, qualifier), decoder);
  }
  
  public void setDecoder(byte[] family, byte[] qualifier, long start, long stop, Decoder decoder) {
    Column column = new Column(family, qualifier);
    Interval interval = new Interval(start, stop);
    
    if (!versionedColumns.containsKey(column)) {
      versionedColumns.put(column, new TreeMap<Interval, Decoder>());
    }
    
    TreeMap<Interval, Decoder> decoders = versionedColumns.get(column);
    Interval prev = decoders.floorKey(interval);
    
    if (prev != null && prev.overlaps(interval)) {
      throw new RuntimeException("The new interval overlaps an existing interval");
    }
    
    decoders.put(interval, decoder);
  }
  
  public Decoder getDecoder() {
    return keyColumn;
  }
  
  public Decoder getDecoder(byte[] family, byte[] qualifier) {
    return defaultColumns.get(new Column(family, qualifier));
  }
  
  public Decoder getDecoder(byte[] family, byte[] qualifier, long timestamp) {
    Column column = new Column(family, qualifier);
    Decoder defaultDecoder = defaultColumns.get(column);
    Interval interval = new Interval(timestamp, timestamp);
    
    TreeMap<Interval, Decoder> columns = versionedColumns.get(column);
    if (columns == null) return defaultDecoder;
    
    Interval key = columns.floorKey(interval);
    if (key.overlaps(interval)) {
      return columns.get(key);
    }
    
    return defaultDecoder;
  }
  
  public HTable(Configuration conf, byte[] tableName) throws IOException {
    super(conf, tableName);
  }
  
  public HTable(byte[] tableName) throws IOException {
    super(tableName);
  }
  
  public void setPackedBinaryDecoder(String decodeString) {
    PackedBinary decoder = new PackedBinary(decodeString);
    setDecoder(decoder);
  }
  
  public void setPackedBinaryDecoder(byte[] family, byte[] qualifier, String decodeString) {
    PackedBinary decoder = new PackedBinary(decodeString);
    setDecoder(family, qualifier, decoder);
  }
  
  public void setPackedBinaryDecoder(byte[] family, byte[] qualifier, long start, long stop, String decodeString) {
    PackedBinary decoder = new PackedBinary(decodeString);
    setDecoder(family, qualifier, start, stop, decoder);
  }
  
  public Object getDecoded(Get get) throws IOException {
    Result result = get(get);
    if (result.isEmpty()) return null;
    
    List<KeyValue> kvs = result.list();
    Object[] row = new Object[kvs.size()];
    
    int index = 0;
    for (KeyValue kv : kvs) {
      Object[] column = new Object[4];
      row[index++] = column;
      
      byte[] family = kv.getFamily();
      column[0] = Bytes.toStringBinary(family);
      
      byte[] qualifier = kv.getQualifier();
      column[1] = Bytes.toStringBinary(qualifier);
      
      long timestamp = kv.getTimestamp();
      column[2] = timestamp;
      
      byte[] value = kv.getValue();
      column[3] = Bytes.toStringBinary(value);
      
      Decoder decoder = getDecoder(family, qualifier, timestamp);
      if (decoder != null) {
        column[3] = decoder.decode(kv.getValue());
      }
    }
    
    return row;
  }
}
