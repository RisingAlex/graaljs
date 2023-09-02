/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.scriptengine.test;

import static org.junit.Assert.assertEquals;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import org.graalvm.polyglot.Context;
import org.junit.Test;

public class GR45640 {

    private static GraalJSScriptEngine createEngine(boolean scripting) {
        Context.Builder builder = Context.newBuilder("js");
        if (scripting) {
            builder.allowExperimentalOptions(true).option("js.scripting", "true");
        }
        return GraalJSScriptEngine.create(null, builder);
    }

    @Test
    public void testInput() {
        InputStream originalIn = System.in;
        try {
            ByteArrayInputStream in = new ByteArrayInputStream("foo\n".getBytes());
            System.setIn(in);
            String result = createEngine(true).getPolyglotContext().eval("js", "readLine();").asString();
            assertEquals("foo", result);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    public void testOutput() {
        PrintStream originalOut = System.out;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(10);
            System.setOut(new PrintStream(out));
            createEngine(false).getPolyglotContext().eval("js", "print('foo');");
            assertEquals("foo\n", out.toString());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    public void testErrorOutput() {
        PrintStream originalErr = System.err;
        try {
            ByteArrayOutputStream err = new ByteArrayOutputStream(10);
            System.setErr(new PrintStream(err));
            createEngine(false).getPolyglotContext().eval("js", "printErr('foo');");
            assertEquals("foo\n", err.toString());
        } finally {
            System.setErr(originalErr);
        }
    }

}
