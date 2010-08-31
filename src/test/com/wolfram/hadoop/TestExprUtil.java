package com.wolfram.hadoop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.*;

import com.wolfram.jlink.Expr;

import static org.junit.Assert.*;

public class TestExprUtil {

  @Test
  public void testBooleanTrue() {
    Boolean b = new Boolean(true);
    Expr e = ExprUtil.toExpr(b);
    assertTrue(e.trueQ());
    assertEquals(b, ExprUtil.fromExpr(e));
  }

  @Test
  public void testBooleanFalse() {
    Boolean b = new Boolean(false);
    Expr e = ExprUtil.toExpr(b);
    assertFalse(e.trueQ());
    assertEquals(b, ExprUtil.fromExpr(e));
  }

  @Test
  public void testArrayList() {
    ArrayList<Object> l = new ArrayList<Object>();
    l.add(new Boolean(true));
    l.add(new Integer(10));
    l.add(3.14159);
    l.add("foo");
    Expr e = ExprUtil.toExpr(l);
    assertTrue(e.listQ());
    int[] dimensions = e.dimensions();
    assertEquals(dimensions.length, 1);
    assertEquals(dimensions[0], 4);
    assertEquals(l, ExprUtil.fromExpr(e));
  }

  @Test
  public void testList() {
    List<Object> l = new LinkedList<Object>();
    l.add(new Boolean(true));
    l.add(new Integer(10));
    l.add(3.14159);
    l.add("foo");
    Expr e = ExprUtil.toExpr(l);
    assertTrue(e.listQ());
    int[] dimensions = e.dimensions();
    assertEquals(dimensions.length, 1);
    assertEquals(dimensions[0], 4);
    assertEquals(l, ExprUtil.fromExpr(e));
  }

  @Test
  public void testMap() {
    Map<Object, Object> m = new HashMap<Object, Object>();
    m.put("foo", "bar");
    m.put("Pi", 3.14159);
    Expr e = ExprUtil.toExpr(m);
    assertTrue(e.listQ());
    int[] dimensions = e.dimensions();
    assertEquals(dimensions.length, 1);
    assertEquals(dimensions[0], 2);
    assertEquals(m, ExprUtil.fromExpr(e));
  }

  @Test
  public void testListNesting() {
    List<Object> l1 = new ArrayList<Object>();
    l1.add(1);
    l1.add(2);
    List<Object> l2 = new ArrayList<Object>();
    l2.add("foo");
    l2.add("bar");
    l1.add(l2);
    Expr e1 = ExprUtil.toExpr(l1);
    assertTrue(e1.listQ());
    int[] dimensions = e1.dimensions();
    assertEquals(dimensions.length, 1);
    assertEquals(dimensions[0], 3);
    Expr e2 = e1.part(3);
    dimensions = e2.dimensions();
    assertEquals(dimensions.length, 1);
    assertEquals(dimensions[0], 2);
  }
}
