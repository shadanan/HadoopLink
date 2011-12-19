package com.wolfram.hbase;

import java.io.IOException;
import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;

public class HTable extends org.apache.hadoop.hbase.client.HTable {
  private HashMap<String, Decoder> decoders = new HashMap<String, Decoder>();
  
  public HTable(Configuration conf, String tableName) throws IOException {
    super(conf, tableName);
  }
  
  public HTable(String tableName) throws IOException {
    super(tableName);
  }
  
  public void setDecoder(Decoder decode) {
    setDecoder(null, decode);
  }
  
  public void setDecoder(String field, Decoder decode) {
    decoders.put(field, decode);
  }
  
  public void setPackedBinaryDecoder(String decodeString) {
    setPackedBinaryDecoder(null, decodeString);
  }
  
  public void setPackedBinaryDecoder(String field, String decodeString) {
    PackedBinary pack = new PackedBinary(decodeString);
    setDecoder(field, pack);
  }
}
