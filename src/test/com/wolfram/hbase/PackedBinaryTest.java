package com.wolfram.hbase;

import static org.junit.Assert.*;

import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

public class PackedBinaryTest {
  public static String toString(Object obj) {
    StringBuffer sb = new StringBuffer();
    
    if (obj.getClass() == Object[].class) {
      Object[] parts = (Object[])obj;
      sb.append("[");
      for (int i = 0; i < parts.length; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        
        if (parts[i].getClass().equals(String.class)) {
          sb.append("\"");
          sb.append(Bytes.toStringBinary(
              Bytes.toBytesBinary((String)parts[i])));
          sb.append("\"");
        } else {
          sb.append(parts[i].toString());
        }
      }
      sb.append("]");
    } else {
      if (obj.getClass().equals(String.class)) {
        sb.append("\"");
        sb.append(Bytes.toStringBinary(
            Bytes.toBytesBinary((String)obj)));
        sb.append("\"");
      } else {
        sb.append(obj.toString());
      }
    }
    
    return sb.toString();
  }
  
  @Test
  public void test1() {
    String expected = "\"hello\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\"";
    String actual = toString(PackedBinary.decode("a40", true, Bytes.toBytesBinary("hello")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test1b() {
    String expected = "hello\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00";
    String actual = Bytes.toStringBinary(PackedBinary.encode("a40", true, "hello"));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test2() {
    String expected = "\"hello\"";
    String actual = toString(PackedBinary.decode("A*", true, Bytes.toBytesBinary("hello")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test2b() {
    String expected = "hello";
    String actual = Bytes.toStringBinary(PackedBinary.encode("A*", true, "hello"));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test3() {
    String expected = "[32768, \"hello world\", 16384]";
    String actual = toString(PackedBinary.decode("la-4l", Bytes.toBytesBinary("\000\000\200\000hello world\000\000@\000")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test3b() {
    String expected = "\\x00\\x00\\x80\\x00hello world\\x00\\x00@\\x00";
    String actual = Bytes.toStringBinary(PackedBinary.encode("la-4l", 32768, "hello world", 16384));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test4() {
    String expected = "[\"hello world\", 16384]";
    String actual = toString(PackedBinary.decode("a-4l", Bytes.toBytesBinary("hello world\000\000@\000")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test4b() {
    String expected = "hello world\\x00\\x00@\\x00";
    String actual = Bytes.toStringBinary(PackedBinary.encode("a-4l", "hello world", 16384));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test5() {
    String expected = "[\"hello\", 0, \"world\"]";
    String actual = toString(PackedBinary.decode("a5ca5", Bytes.toBytesBinary("hello\000world")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test5b() {
    String expected = "hello\\x00world";
    String actual = Bytes.toStringBinary(PackedBinary.encode("a5ca5", "hello", 0, "world"));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test6() {
    String expected = "\"hello world\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x18\"";
    String actual = toString(PackedBinary.decode("A*", true, Bytes.toBytesBinary("hello world\000\000\000\000\000\000\000\030")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test7() {
    String expected = "[42, \"the meaning of life\", 24]";
    String actual = toString(PackedBinary.decode("lx\000z*x\000l", Bytes.toBytesBinary("\000\000\000*\000the meaning of life\000\000\000\000\030")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test7b() {
    String expected = "\\x00\\x00\\x00*\\x00the meaning of life\\x00\\x00\\x00\\x00\\x18";
    String actual = Bytes.toStringBinary(PackedBinary.encode("lx\000z*x\000l", 42, "the meaning of life", 24));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test8() {
    String expected = "\"hello world\"";
    String actual = toString(PackedBinary.decode("A*", true, Bytes.toBytesBinary("hello world\000\000\000\000\000\000\000\000")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test9() {
    String expected = "[\"string1\", \"string2\", \"string3\"]";
    String actual = toString(PackedBinary.decode("y:*x:y:*x:y:*", Bytes.toBytesBinary("string1:string2:string3")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test9b() {
    String expected = "string1:string2:string3";
    String actual = Bytes.toStringBinary(PackedBinary.encode("y:*x:y:*x:y:*", "string1", "string2", "string3"));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test10() {
    String expected = "[\"string1\", 58, \"string2\", 58, \"st\"]";
    String actual = toString(PackedBinary.decode("y:*cy:*cz2", Bytes.toBytesBinary("string1:string2:string3")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test10b() {
    String expected = "string1:string2:st";
    String actual = Bytes.toStringBinary(PackedBinary.encode("y:*cy:*cz2", "string1", 58, "string2", 58, "st"));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test11() {
    String expected = "[\"hello world!\", 16384]";
    String actual = toString(PackedBinary.decode("Y:*l", Bytes.toBytesBinary("hello world!:\000\000@\000")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test11b() {
    String expected = "hello world!:\\x00\\x00@\\x00";
    String actual = Bytes.toStringBinary(PackedBinary.encode("Y:*l", "hello world!", 16384));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test12() {
    String expected = "[\"api.wolframalpha.com\", \"/Calculate/api/v1/query.jsp\", 1317272400, \"\", \"-\", \"-\"]";
    String actual = toString(PackedBinary.decode("Z*Z*lx\000Z*Z*z*", Bytes.toBytesBinary(
        "api.wolframalpha.com\\x00/Calculate/api/v1/query.jsp\\x00N\\x83\\xFBP\\x00\\x00-\\x00-")));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test12b() {
    String expected = "api.wolframalpha.com\\x00/Calculate/api/v1/query.jsp\\x00N\\x83\\xFBP\\x00\\x00-\\x00-";
    String actual = Bytes.toStringBinary(PackedBinary.encode("Z*Z*lx\000Z*Z*z*",
        "api.wolframalpha.com", "/Calculate/api/v1/query.jsp", 1317272400, "", "-", "-"));
    assertEquals(expected, actual);
  }
  
  @Test
  public void test13() {
    String expected = "[\"api.wolframalpha.com\", \"\", 1322535600, \"www.wolframalpha.com\", \"/\"]";
    String actual = toString(PackedBinary.decode("Z*Z*lx\000Z*z*", Bytes.toBytesBinary(
        "api.wolframalpha.com\\x00\\x00N\\xD4J\\xB0\\x00www.wolframalpha.com\\x00/")));
    assertEquals(expected, actual);
  }

  @Test
  public void test13b() {
    String expected = "api.wolframalpha.com\\x00\\x00N\\xD4J\\xB0\\x00www.wolframalpha.com\\x00/";
    String actual = Bytes.toStringBinary(PackedBinary.encode("Z*Z*lx\000Z*z*", 
        "api.wolframalpha.com", "", 1322535600, "www.wolframalpha.com", "/"));
    assertEquals(expected, actual);
  }

  @Test
  public void test14() {
    String expected = "[\"api.wolframalpha.com\", \"/Calculate/api/v1/query.jsp\", 1317326400, \"10.12.4.97.1317326481746776\"]";
    String actual = toString(PackedBinary.decode("Z*Z*lx\000z*", Bytes.toBytesBinary(
        "api.wolframalpha.com\\x00/Calculate/api/v1/query.jsp\\x00N\\x84\\xCE@\\x0010.12.4.97.1317326481746776")));
    assertEquals(expected, actual);
  }

  @Test
  public void test14b() {
    String expected = "api.wolframalpha.com\\x00/Calculate/api/v1/query.jsp\\x00N\\x84\\xCE@\\x0010.12.4.97.1317326481746776";
    String actual = Bytes.toStringBinary(PackedBinary.encode("Z*Z*lx\000z*", 
        "api.wolframalpha.com", "/Calculate/api/v1/query.jsp", 1317326400, "10.12.4.97.1317326481746776"));
    assertEquals(expected, actual);
  }

  @Test
  public void test15() {
    String expected = "[1288414800, \"169.231.13.43.1288412012191129\"]";
    String actual = toString(PackedBinary.decode("lA*", Bytes.toBytesBinary(
        "L\\xCB\\xA6P169.231.13.43.1288412012191129")));
    assertEquals(expected, actual);
  }

  @Test
  public void test15b() {
    String expected = "L\\xCB\\xA6P169.231.13.43.1288412012191129";
    String actual = Bytes.toStringBinary(PackedBinary.encode("lA*", 
        1288414800, "169.231.13.43.1288412012191129"));
    assertEquals(expected, actual);
  }

  @Test
  public void test16() {
    String expected = "[1308622848, \"a3ef2b4b691c4dffffff7e15\"]";
    String actual = toString(PackedBinary.decode("lA*", Bytes.toBytesBinary(
        "N\\x00\\x00\\x00a3ef2b4b691c4dffffff7e15")));
    assertEquals(expected, actual);
  }

  @Test
  public void test16b() {
    String expected = "N\\x00\\x00\\x00a3ef2b4b691c4dffffff7e15";
    String actual = Bytes.toStringBinary(PackedBinary.encode("lA*", 
        1308622848, "a3ef2b4b691c4dffffff7e15"));
    assertEquals(expected, actual);
  }

  @Test
  public void test17() {
    String expected = "[1308622929, \"onodera@hulinks.co.jp\"]";
    String actual = toString(PackedBinary.decode("lA*", Bytes.toBytesBinary(
        "N\\x00\\x00Qonodera@hulinks.co.jp")));
    assertEquals(expected, actual);
  }

  @Test
  public void test17b() {
    String expected = "N\\x00\\x00Qonodera@hulinks.co.jp";
    String actual = Bytes.toStringBinary(PackedBinary.encode("lA*", 
        1308622929, "onodera@hulinks.co.jp"));
    assertEquals(expected, actual);
  }

  @Test
  public void test18() {
    String expected = "[\"api.wolframalpha.com\", 1296540001, \"colo4b-webprd3.wolfram.com\", 424]";
    String actual = toString(PackedBinary.decode("Y:*lx:Y:*q", Bytes.toBytesBinary(
        "api.wolframalpha.com:MG\\xA1a:colo4b-webprd3.wolfram.com:\\x00\\x00\\x00\\x00\\x00\\x00\\x01\\xA8")));
    assertEquals(expected, actual);
  }

  @Test
  public void test18b() {
    String expected = "api.wolframalpha.com:MG\\xA1a:colo4b-webprd3.wolfram.com:\\x00\\x00\\x00\\x00\\x00\\x00\\x01\\xA8";
    String actual = Bytes.toStringBinary(PackedBinary.encode("Y:*lx:Y:*q", 
        "api.wolframalpha.com", 1296540001, "colo4b-webprd3.wolfram.com", 424l));
    assertEquals(expected, actual);
  }

  @Test
  public void test19() {
    String expected = "[\"webstats_bin_cookie_session_exits\", 1318482000]";
    String actual = toString(PackedBinary.decode("Z*l", Bytes.toBytesBinary(
        "webstats_bin_cookie_session_exits\\x00N\\x96pP")));
    assertEquals(expected, actual);
  }

  @Test
  public void test19b() {
    String expected = "webstats_bin_cookie_session_exits\\x00N\\x96pP";
    String actual = Bytes.toStringBinary(PackedBinary.encode("Z*l",
        "webstats_bin_cookie_session_exits", 1318482000));
    assertEquals(expected, actual);
  }
}
