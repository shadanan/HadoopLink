package com.wolfram.hbase;

public interface Decoder {
  public Object decode(byte[] data);
}
