package com.hubspot.jinjava.el.ext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Maps;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.FatalTemplateErrorsException;

public class TruncDivTest {

    @Before
    public void setUp() {
        jinja = new Jinjava();
    }

    /**
     * Test the truncated division operator "//" with integer values
     */
    @Test
    public void testTruncDivInteger() {
        Map<String, Object> context = Maps.newHashMap();
        context.put("dividend", 5);
        context.put("divisor",  2);

        String template = "{% set x = dividend // divisor %}{{x}}";
        String rendered = jinja.render(template, context);
        assertEquals("2", rendered);
    }

    /**
     * Test the truncated division operator "//" with fractional values
     */
    @Test
    public void testTruncDivFractional() {

        Map<String, Object> context = Maps.newHashMap();
        context.put("dividend", 5.0);
        context.put("divisor",  2);

        String template = "{% set x = dividend // divisor %}{{x}}";
        String rendered = jinja.render(template, context);
        assertEquals("2.0", rendered);
    }

    /**
     * Test the truncated division operator "//" with strings
     */
    @Test
    public void testTruncDivStringFails() {

        Map<String, Object> context = Maps.newHashMap();
        context.put("dividend", "5");
        context.put("divisor",  "2");

        String template = "{% set x = dividend // divisor %}{{x}}";
        try {
            jinja.render(template, context);
        } catch (FatalTemplateErrorsException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Unsupported operand type(s)"));
        }
    }


    private Jinjava jinja;
}
