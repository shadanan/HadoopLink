package com.wolfram.hbase;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.hadoop.hbase.util.Bytes;

public class PackedBinary implements Transcoder {
  private class Format {
    char directive;
    boolean bigEndian = true;
    int repeat = 1;
    byte delim = 0;
    
    public Format(String format) {
      int start = 1;
      directive = format.charAt(0);
      
      if (isCharInString(directive, "yYxX")) {
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
      
      if (directive == 'x' && repeat == 0) {
        throw new RuntimeException("Invalid repeat count (*) for directive 'x'");
      }
    }
    
    @Override
    public String toString() {
      String result = "";
      result += "Directive: " + directive;
      result += ", Repeat: " + repeat;
      result += ", Delim: " + delim;
      if (bigEndian) result += " (big endian)";
      else result += " (little endian)";
      return result;
    }
  }
  
  private static final String MODIFIERS = "1234567890-*<>";
  
  private Format[] directives;
  private boolean single;
  
  public PackedBinary(String format) {
    this(format, false);
  }
  
  public PackedBinary(String format, boolean single) {
    this.single = single;
    ArrayList<String> dirs = new ArrayList<String>();
    
    int pos = 0;
    while (pos < format.length()) {
      int directiveSize;
      int lookAhead;
      
      if (isCharInString(format.charAt(pos), "yYxX")) {
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
  
  private static byte[] reverse(byte[] bytes) {
    byte[] result = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      result[bytes.length - 1 - i] = bytes[i];
    }
    return result;
  }
  
  private static byte[] paddedBytes(String string, int length, byte pad) {
    byte[] result = new byte[length];
    byte[] data = Bytes.toBytes(string);
    Arrays.fill(result, pad);
    Bytes.putBytes(result, 0, data, 0, Math.min(length, data.length));
    return result;
  }
  
  private static byte castByte(Object o) {
    if (o.getClass() == Byte.class) {
      return ((Byte)o).byteValue();
    } else if (o.getClass() == Short.class) {
      return ((Short)o).byteValue();
    } else if (o.getClass() == Integer.class) {
      return ((Integer)o).byteValue();
    } else if (o.getClass() == Long.class) {
      return ((Long)o).byteValue();
    }
    throw new ClassCastException("Could not cast " + o + " to byte");
  }
  
  private static short castShort(Object o) {
    if (o.getClass() == Byte.class) {
      return ((Byte)o).shortValue();
    } else if (o.getClass() == Short.class) {
      return ((Short)o).shortValue();
    } else if (o.getClass() == Integer.class) {
      return ((Integer)o).shortValue();
    } else if (o.getClass() == Long.class) {
      return ((Long)o).shortValue();
    }
    throw new ClassCastException("Could not cast " + o + " to short");
  }
  
  private static int castInteger(Object o) {
    if (o.getClass() == Byte.class) {
      return ((Byte)o).intValue();
    } else if (o.getClass() == Short.class) {
      return ((Short)o).intValue();
    } else if (o.getClass() == Integer.class) {
      return ((Integer)o).intValue();
    } else if (o.getClass() == Long.class) {
      return ((Long)o).intValue();
    }
    throw new ClassCastException("Could not cast " + o + " to int");
  }
  
  private static long castLong(Object o) {
    if (o.getClass() == Byte.class) {
      return ((Byte)o).longValue();
    } else if (o.getClass() == Short.class) {
      return ((Short)o).longValue();
    } else if (o.getClass() == Integer.class) {
      return ((Integer)o).longValue();
    } else if (o.getClass() == Long.class) {
      return ((Long)o).longValue();
    }
    throw new ClassCastException("Could not cast " + o + " to long");
  }
  
  private static float castFloat(Object o) {
    if (o.getClass() == Float.class) {
      return ((Float)o).floatValue();
    } else if (o.getClass() == Double.class) {
      return ((Double)o).floatValue();
    } else if (o.getClass() == Byte.class) {
      return ((Byte)o).floatValue();
    } else if (o.getClass() == Short.class) {
      return ((Short)o).floatValue();
    } else if (o.getClass() == Integer.class) {
      return ((Integer)o).floatValue();
    } else if (o.getClass() == Long.class) {
      return ((Long)o).floatValue();
    } 
    throw new ClassCastException("Could not cast " + o + " to float");
  }
  
  private static double castDouble(Object o) {
    if (o.getClass() == Double.class) {
      return ((Double)o).doubleValue();
    } else if (o.getClass() == Float.class) {
      return ((Float)o).doubleValue();
    } else if (o.getClass() == Byte.class) {
      return ((Byte)o).doubleValue();
    } else if (o.getClass() == Short.class) {
      return ((Short)o).doubleValue();
    } else if (o.getClass() == Integer.class) {
      return ((Integer)o).doubleValue();
    } else if (o.getClass() == Long.class) {
      return ((Long)o).doubleValue();
    } 
    throw new ClassCastException("Could not cast " + o + " to double");
  }
  
  @Override
  public byte[] encode(Object... objects) {
    byte[] result = new byte[0];
    byte[] single = new byte[1];
    
    int pos = 0;
    for (Format format : directives) {
      if (format.directive == 'x') {
        byte[] pad = new byte[format.repeat];
        Arrays.fill(pad, format.delim);
        result = Bytes.add(result, pad);
        continue;
      }
      
      if (format.directive == 'c') {
        for (int i = 0; pos < objects.length && format.repeat == 0 || i < format.repeat; i++) {
          single[0] = castByte(objects[pos]);
          result = Bytes.add(result, single);
          pos += 1;
        }
        continue;
      }
      
      if (format.directive == 's') {
        for (int i = 0; pos < objects.length && format.repeat == 0 || i < format.repeat; i++) {
          if (format.bigEndian) {
            result = Bytes.add(result, Bytes.toBytes(castShort(objects[pos])));
          } else {
            result = Bytes.add(result, reverse(Bytes.toBytes(castShort(objects[pos]))));
          }
          pos += 1;
        }
        continue;
      }
      
      if (format.directive == 'l') {
        for (int i = 0; pos < objects.length && format.repeat == 0 || i < format.repeat; i++) {
          if (format.bigEndian) {
            result = Bytes.add(result, Bytes.toBytes(castInteger(objects[pos])));
          } else {
            result = Bytes.add(result, reverse(Bytes.toBytes(castInteger(objects[pos]))));
          }
          pos += 1;
        }
        continue;
      }

      if (format.directive == 'q') {
        for (int i = 0; pos < objects.length && format.repeat == 0 || i < format.repeat; i++) {
          if (format.bigEndian) {
            result = Bytes.add(result, Bytes.toBytes(castLong(objects[pos])));
          } else {
            result = Bytes.add(result, reverse(Bytes.toBytes(castLong(objects[pos]))));
          }
          pos += 1;
        }
        continue;
      }
      
      if (format.directive == 'd') {
        for (int i = 0; pos < objects.length && format.repeat == 0 || i < format.repeat; i++) {
          if (format.bigEndian) {
            result = Bytes.add(result, Bytes.toBytes(castDouble(objects[pos])));
          } else {
            result = Bytes.add(result, reverse(Bytes.toBytes(castDouble(objects[pos]))));
          }
          pos += 1;
        }
        continue;
      }
      
      if (format.directive == 'f') {
        for (int i = 0; pos < objects.length && format.repeat == 0 || i < format.repeat; i++) {
          if (format.bigEndian) {
            result = Bytes.add(result, Bytes.toBytes(castFloat(objects[pos])));
          } else {
            result = Bytes.add(result, reverse(Bytes.toBytes(castFloat(objects[pos]))));
          }
          pos += 1;
        }
        continue;
      }
      
      if (format.directive == 'a' || format.directive == 'A') {
        byte pad = 0;
        if (format.directive == 'A') {
          pad = 32;
        }
        
        if (format.repeat <= 0) {
          result = Bytes.add(result, Bytes.toBytes((String)objects[pos]));
        } else {
          result = Bytes.add(result, paddedBytes((String)objects[pos], format.repeat, pad));
        }
        pos += 1;
        continue;
      }
      
      if (format.directive == 'Y' || format.directive == 'Z') {
        single[0] = format.delim;
        result = Bytes.add(result, Bytes.toBytes((String)objects[pos]), single);
        pos += 1;
        continue;
      }
      
      if (format.directive == 'y' || format.directive == 'z') {
        result = Bytes.add(result, Bytes.toBytes((String)objects[pos]));
        pos += 1;
        continue;
      }
      
      throw new RuntimeException("Unrecognized / unimplemented directive: " + format.directive);
    }
    
    return result;
  }
  
  @Override
  public Object decode(byte[] data) {
    ArrayList<Object> result = new ArrayList<Object>();
    
    int pos = 0;
    for (Format format : directives) {
      if (format.directive == 'x') {
        pos += format.repeat;
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
    
    if (single) {
      if (result.size() == 0) {
        return null;
      } else {
        return result.get(0);
      }
    } else {
      return result.toArray();
    }
  }
  
  public static Object decode(String format, byte[] data) {
    return decode(format, false, data);
  }
  
  public static Object decode(String format, boolean single, byte[] data) {
    PackedBinary p = new PackedBinary(format, single);
    return p.decode(data);
  }
  
  public static byte[] encode(String format, Object... objects) {
    return encode(format, false, objects);
  }
  
  public static byte[] encode(String format, boolean single, Object... objects) {
    PackedBinary p = new PackedBinary(format, single);
    return p.encode(objects);
  }
}
