package com.wolfram.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
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
  
  private Transcoder keyColumn = null;
  private HashMap<Column, Transcoder> defaultColumns = new HashMap<Column, Transcoder>();
  private HashMap<Column, TreeMap<Interval, Transcoder>> versionedColumns = new HashMap<Column, TreeMap<Interval, Transcoder>>();
  
  public void setTranscoder(Transcoder transcoder) {
    keyColumn = transcoder;
  }
  
  public void setTranscoder(byte[] family, byte[] qualifier, Transcoder transcoder) {
    defaultColumns.put(new Column(family, qualifier), transcoder);
  }
  
  public void setTranscoder(byte[] family, byte[] qualifier, long start, long stop, Transcoder transcoder) {
    Column column = new Column(family, qualifier);
    Interval interval = new Interval(start, stop);
    
    if (!versionedColumns.containsKey(column)) {
      versionedColumns.put(column, new TreeMap<Interval, Transcoder>());
    }
    
    TreeMap<Interval, Transcoder> transcoders = versionedColumns.get(column);
    Interval prev = transcoders.floorKey(interval);
    
    if (prev != null && prev.overlaps(interval)) {
      throw new RuntimeException("The new interval overlaps an existing interval");
    }
    
    transcoders.put(interval, transcoder);
  }
  
  public Transcoder getTranscoder() {
    return keyColumn;
  }
  
  public Transcoder getTranscoder(byte[] family, byte[] qualifier) {
    return defaultColumns.get(new Column(family, qualifier));
  }
  
  public Transcoder getTranscoder(byte[] family, byte[] qualifier, long timestamp) {
    Column column = new Column(family, qualifier);
    Transcoder defaultTranscoder = defaultColumns.get(column);
    Interval interval = new Interval(timestamp, timestamp);
    
    TreeMap<Interval, Transcoder> columns = versionedColumns.get(column);
    if (columns == null) return defaultTranscoder;
    
    Interval key = columns.floorKey(interval);
    if (key.overlaps(interval)) {
      return columns.get(key);
    }
    
    return defaultTranscoder;
  }
  
  public HTable(Configuration conf, byte[] tableName) throws IOException {
    super(conf, tableName);
  }
  
  public HTable(byte[] tableName) throws IOException {
    super(tableName);
  }
  
  // Cache variables
  private long count = 0;
  private byte[] row = null;
  private ResultScanner scanner;
  
  private Object[] decodeKeyValues(KeyValue[] kvs) {
    return decodeKeyValues(kvs, null);
  }
  
  private Object[] decodeKeyValues(KeyValue[] kvs, byte[] id) {
    Object[] row = null;
    int index = 0;
    
    if (id != null) {
      row = new Object[kvs.length + 1];
      Transcoder transcoder = getTranscoder();
      row[index++] = transcoder.decode(id);
    } else {
      row = new Object[kvs.length];
    }
    
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
      
      Transcoder transcoder = getTranscoder(family, qualifier, timestamp);
      if (transcoder != null) {
        column[3] = transcoder.decode(kv.getValue());
      }
    }
    
    return row;
  }
  
  // Get
  public Object getDecoded(Get get) throws IOException {
    Result result = get(get);
    if (result.isEmpty()) return null;
    return decodeKeyValues(result.raw());
  }

  // Count
  public long countDecoded(int caching) throws IOException {
    Scan scan = new Scan();
    scan.setCacheBlocks(false);
    scan.setCaching(caching);
    scan.setFilter(new FirstKeyOnlyFilter());

    ResultScanner scanner = getScanner(scan);
    
    count = 0;
    Iterator<Result> iterator = scanner.iterator();
    
    while (iterator.hasNext()) {
      Result result = iterator.next();
      row = result.getRow();
      count++;
    }
    
    long result = count;
    count = 0;
    row = null;
    return result;
  }
  
  public Object getCurrentRow() {
    if (row == null) {
      return "";
    } else if (keyColumn != null) {
      return keyColumn.decode(row);
    } else {
      return Bytes.toStringBinary(row);
    }
  }
  
  public long getCurrentCount() {
    return count;
  }
  
  // Scan
  public void setScan(Scan scan) throws IOException {
    count = 0;
    row = null;
    scanner = getScanner(scan);
  }
  
  public Object[] scanDecoded(int size) throws IOException {
    ArrayList<Object> decodedResults;
    if (size == -1) {
      decodedResults = new ArrayList<Object>();
    } else {
      decodedResults = new ArrayList<Object>(size);
    }
    
    for (int i = 0; i < size || size == -1; i++) {
      Result result = scanner.next();
      if (result == null) break;
      
      count++;
      row = result.getRow();
      
      decodedResults.add(decodeKeyValues(result.raw(), row));
    }
    
    return decodedResults.toArray();
  }

  public Object[] scanDecoded() throws IOException {
    return scanDecoded(-1);
  }
}
