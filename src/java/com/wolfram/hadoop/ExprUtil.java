package com.wolfram.hadoop;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;

import org.apache.hadoop.record.Buffer;

/**
 * Utility class for serializing a subset of Java types to Expr
 * objects.
 */
public class ExprUtil {

  @SuppressWarnings("unchecked")
  public static Expr toExpr(Object obj) {
    Expr expr = null;
    if (obj instanceof Buffer) {
    } else if (obj instanceof Byte) {
      expr = byteToExpr((Byte) obj);
    } else if (obj instanceof Boolean) {
      expr = booleanToExpr((Boolean) obj);
    } else if (obj instanceof Integer) {
      expr = new Expr((Integer) obj);
    } else if (obj instanceof Long) {
      expr = new Expr((Long) obj);
    } else if (obj instanceof Float) {
      expr = new Expr((Float) obj);
    } else if (obj instanceof Double) {
      expr = new Expr((Double) obj);
    } else if (obj instanceof String) {
      expr = new Expr((String) obj);
    } else if (obj instanceof ArrayList) {
      expr = listToExpr((List<Object>) obj);
    } else if (obj instanceof List) {
      expr = listToExpr((List<Object>) obj);
    } else if (obj instanceof Map) {
      expr = mapToExpr((Map<Object, Object>) obj);
    } else {
      String error = String.format("%s cannot be converted to an Expr",
                                   obj.getClass());
      throw new RuntimeException(error);
    }
    return expr;
  }
  
  private static Expr byteToExpr(Byte b) {
    return new Expr(b);
  }
  
  private static Expr booleanToExpr(Boolean b) {
    if (b) {
      return new Expr(Expr.SYMBOL, "True");
    } else {
      return new Expr(Expr.SYMBOL, "False");
    }
  }

  private static Expr listToExpr(List<Object> list) {
    int length = list.size();
    Expr[] expressions = new Expr[length];
    for (int i = 0; i < length; i++) {
      expressions[i] = toExpr(list.get(i));
    }
    return new Expr(new Expr(Expr.SYMBOL, "List"), expressions);
  }

  private static Expr mapToExpr(Map<Object, Object> map) {
    int length = map.size();
    Expr[] rules = new Expr[length];
    int i = 0;
    for (Object key : map.keySet()) {
      Expr lhs = toExpr(key);
      Expr rhs = toExpr(map.get(key));
      Expr rule = new Expr(new Expr(Expr.SYMBOL, "Rule"),
                           new Expr[] {lhs, rhs});
      rules[i] = rule;
      i++;
    }
    return new Expr(new Expr(Expr.SYMBOL, "List"), rules);
  }

  public static Object fromExpr(Expr expr) {
    Object obj = null;
    try {
      if (booleanQ(expr)) {
        if (expr.trueQ()) {
          obj = new Boolean(true);
        } else {
          obj = new Boolean(false);
        }
      } else if (expr.integerQ()) {
        BigInteger val = expr.asBigInteger();
        /* truncate to long, the largest integer type handled by typedbytes */
        long n = val.longValue();
        /* if small enough, return as an Integer */
        if (n > Integer.MAX_VALUE || n < Integer.MIN_VALUE) {
          obj = new Long(n);
        } else {
          obj = new Integer((int) n);
        }
      } else if (expr.realQ() || expr.rationalQ()) {
        /* return all real numbers as doubles */
        BigDecimal val = expr.asBigDecimal();
        obj = new Double(val.doubleValue());
      } else if (expr.stringQ() || expr.symbolQ()) {
        obj = expr.asString();
      } else {
        String error = String.format("%s cannot be converted from an Expr",
                                     expr);
        throw new RuntimeException(error);
      }
    } catch (ExprFormatException e) {
      // TODO: do something useful
    }
    return obj;
  }

  private static boolean booleanQ(Expr expr) throws ExprFormatException {
    if (!expr.symbolQ()) {
      return false;
    }
    String name = expr.asString();
    if (name.equals("True") || name.equals("False")) {
      return true;
    }
    return false;
  }
}
