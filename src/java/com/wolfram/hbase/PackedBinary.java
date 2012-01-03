package com.wolfram.hbase;

import java.util.ArrayList;

import org.apache.hadoop.hbase.util.Bytes;

public class PackedBinary implements Decoder {
  private class Format {
    char directive;
    boolean bigEndian = true;
    int repeat = 1;
    byte delim = 0;
    
    public Format(String format) {
      int start = 1;
      directive = format.charAt(0);
      
      if (isCharInString(directive, "yY")) {
        delim = (byte)format.charAt(1);
        start = 2;
      }
      
      for (int i = start; i < format.length(); i++) {
        if (format.charAt(i) == '*') {
          repeat = 0;
          continue;
        }
        
        if (format.charAt(i) == '<') {
          bigEndian = false;
          continue;
        }
        
        if (format.charAt(i) == '>') {
          bigEndian = true;
          continue;
        }
        
        if (format.charAt(i) == '!' || format.charAt(i) == '_') {
          throw new RuntimeException("Unimplemented modifier: " + format.charAt(i));
        }
        
        if (isCharInString(format.charAt(i), "-1234567890")) {
          repeat = Integer.parseInt(format.substring(i));
          break;
        }
        
        throw new RuntimeException("Unrecognized modifier: " + format.charAt(i));
      }
    }
  }
  
  private static final String MODIFIERS = "1234567890-*<>";
  
  private Format[] directives;
  
  public PackedBinary(String format) {
    ArrayList<String> dirs = new ArrayList<String>();
    
    int pos = 0;
    while (pos < format.length()) {
      int directiveSize;
      int lookAhead;
      
      if (isCharInString(format.charAt(pos), "yY")) {
        directiveSize = 2;
        lookAhead = pos + 2;
      } else {
        directiveSize = 1;
        lookAhead = pos + 1;
      }
      
      while (lookAhead < format.length()) {
        if (isModifier(format.charAt(lookAhead))) {
          directiveSize++;
        } else {
          break;
        }
        lookAhead++;
      }
      dirs.add(format.substring(pos, pos + directiveSize));
      pos += directiveSize;
    }
    
    directives = new Format[dirs.size()];
    for (int i = 0; i < directives.length; i++) {
      directives[i] = new Format(dirs.get(i));
    }
  }
  
  private boolean isCharInString(char c, String str) {
    return str.indexOf(c) != -1;
  }
  
  private boolean isModifier(char c) {
    return isCharInString(c, MODIFIERS);
  }
  
  private static byte[] reverse(byte[] bytes, int offset, int size) {
    byte[] result = new byte[size];
    for (int i = 0; i < size; i++) {
      result[size - 1 - i] = bytes[i];
    }
    return result;
  }
  
  @Override
  public Object decode(byte[] data) {
    ArrayList<Object> result = new ArrayList<Object>();
    
    int pos = 0;
    for (Format format : directives) {
      if (format.directive == 'x') {
        if (format.repeat == 0) {
          pos = data.length;
        } else {
          pos += format.repeat;
        }
        continue;
      }
      
      if (format.directive == 'X') {
        if (format.repeat == 0) {
          pos = 0;
        } else {
          pos -= format.repeat;
        }
        continue;
      }
      
      if (format.directive == 'c') {
        for (int i = 0; pos < data.length && format.repeat == 0 || i < format.repeat; i++) {
          if (pos >= data.length) {
            result.add(null);
            continue;
          }
          
          result.add(data[pos]);
          pos += 1;
        }
        continue;
      }
      
      if (format.directive == 's') {
        for (int i = 0; pos < data.length && format.repeat == 0 || i < format.repeat; i++) {
          if (pos >= data.length) {
            result.add(null);
            continue;
          }
          
          if (format.bigEndian) {
            result.add(Bytes.toShort(data, pos));
          } else {
            result.add(Bytes.toShort(reverse(data, pos, 2)));
          }
          pos += 2;
        }
        continue;
      }
      
      if (format.directive == 'l') {
        for (int i = 0; pos < data.length && format.repeat == 0 || i < format.repeat; i++) {
          if (pos >= data.length) {
            result.add(null);
            continue;
          }
          
          if (format.bigEndian) {
            result.add(Bytes.toInt(data, pos));
          } else {
            result.add(Bytes.toInt(reverse(data, pos, 4)));
          }
          pos += 4;
        }
        continue;
      }

      if (format.directive == 'q') {
        for (int i = 0; pos < data.length && format.repeat == 0 || i < format.repeat; i++) {
          if (pos >= data.length) {
            result.add(null);
            continue;
          }
          
          if (format.bigEndian) {
            result.add(Bytes.toLong(data, pos));
          } else {
            result.add(Bytes.toLong(reverse(data, pos, 8)));
          }
          pos += 8;
        }
        continue;
      }
      
      if (format.directive == 'd') {
        for (int i = 0; pos < data.length && format.repeat == 0 || i < format.repeat; i++) {
          if (pos >= data.length) {
            result.add(null);
            continue;
          }
          
          if (format.bigEndian) {
            result.add(Bytes.toDouble(data, pos));
          } else {
            result.add(Bytes.toDouble(reverse(data, pos, 8)));
          }
          pos += 8;
        }
        continue;
      }
      
      if (format.directive == 'f') {
        for (int i = 0; pos < data.length && format.repeat == 0 || i < format.repeat; i++) {
          if (pos >= data.length) {
            result.add(null);
            continue;
          }
          
          if (format.bigEndian) {
            result.add(Bytes.toFloat(data, pos));
          } else {
            result.add(Bytes.toFloat(reverse(data, pos, 4)));
          }
          pos += 4;
        }
        continue;
      }
      
      if (format.directive == 'a' || format.directive == 'A') {
        int padlen = 0;
        int end = pos + format.repeat;
        
        if (format.repeat <= 0) {
          end = data.length + format.repeat;
        }
        
        if (end > data.length) {
          padlen = end - data.length;
          end = data.length;
        }
        
        int lastNull = end;
        if (format.directive == 'A') {
          while (lastNull >= pos && data[lastNull - 1] == 0) {
            lastNull--;
          }
        }
        
        StringBuffer str = new StringBuffer(Bytes.toString(data, pos, lastNull - pos));
        for (int i = 0; i < padlen; i++) {
          str.append("\000");
        }
        
        result.add(str.toString());
        pos = end;
        continue;
      }
      
      if (format.directive == 'z' || format.directive == 'Z' || 
          format.directive == 'y' || format.directive == 'Y') {
        int nextDelim = -1;
        for (nextDelim = pos; nextDelim < data.length && format.repeat == 0 || 
            nextDelim < pos + format.repeat; nextDelim++) {
          if (data[nextDelim] == format.delim) break;
        }
        
        int end = pos + format.repeat;
        if (format.repeat == 0) end = nextDelim;
        
        result.add(Bytes.toString(data, pos, Math.min(end, nextDelim) - pos));
        
        if (format.directive == 'Z' || format.directive == 'Y') {
          pos = end + 1;
        } else {
          pos = end;
        }
        
        continue;
      }
      
      throw new RuntimeException("Unrecognized / unimplemented directive: " + format.directive);
    }
    
    if (result.size() == 1) {
      return result.get(0);
    } else if (result.size() == 0) {
      return null;
    } else {
      return result.toArray();
    }
  }
  
  public static Object decode(String decodeString, byte[] data) {
    PackedBinary p = new PackedBinary(decodeString);
    return p.decode(data);
  }
}
