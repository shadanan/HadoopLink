package com.wolfram.hbase;

public interface Transcoder {
  public Object decode(byte[] data);
  public byte[] encode(Object... objects);
}
