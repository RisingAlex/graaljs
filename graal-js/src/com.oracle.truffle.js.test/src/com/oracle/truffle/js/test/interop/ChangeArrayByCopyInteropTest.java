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
package com.oracle.truffle.js.test.interop;

import static com.oracle.truffle.js.lang.JavaScriptLanguage.ID;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.test.JSTest;

public class ChangeArrayByCopyInteropTest {

    private Context context;

    @Before
    public void setUp() {
        context = JSTest.newContextBuilder().option(JSContextOptions.ECMASCRIPT_VERSION_NAME, JSContextOptions.ECMASCRIPT_VERSION_STAGING).build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testToSorted() {
        testWithArray("Array.prototype.toSorted.call(a)",
                        Arrays.asList(50, 40, 30, 20, 10),
                        Arrays.asList(50, 40, 30, 20, 10), // unmodified
                        Arrays.asList(10, 20, 30, 40, 50));
        testWithArray("Array.prototype.toSorted.call(a, (x, y) => y - x)",
                        Arrays.asList(10, 20, 30, 40, 50),
                        Arrays.asList(10, 20, 30, 40, 50), // unmodified
                        Arrays.asList(50, 40, 30, 20, 10));
    }

    @Test
    public void testToReversed() {
        testWithArray("Array.prototype.toReversed.call(a)",
                        Arrays.asList(50, 40, 30, 20, 10),
                        Arrays.asList(50, 40, 30, 20, 10), // unmodified
                        Arrays.asList(10, 20, 30, 40, 50));
    }

    @Test
    public void testToSpliced() {
        testWithArray("Array.prototype.toSpliced.call(a, 1, 2, 42)",
                        Arrays.asList(10, 20, 30, 40, 50),
                        Arrays.asList(10, 20, 30, 40, 50), // unmodified
                        Arrays.asList(10, 42, 40, 50));
    }

    @Test
    public void testWith() {
        testWithArray("Array.prototype.with.call(a, 1, 42)",
                        Arrays.asList(10, 20, 30, 40, 50),
                        Arrays.asList(10, 20, 30, 40, 50), // unmodified
                        Arrays.asList(10, 42, 30, 40, 50));
    }

    private static final TypeLiteral<List<Integer>> LIST_OF_INTEGER = new TypeLiteral<>() {
    };

    private void testWithArray(String test, List<Integer> before, List<Integer> afterExpected, List<Integer> expectedResult) {
        testWithArray(test, before, afterExpected, actualResult -> assertEquals("result", expectedResult, actualResult.as(LIST_OF_INTEGER)));
    }

    private void testWithArray(String test, List<Integer> before, List<Integer> afterExpected, Consumer<Value> resultTest) {
        List<Object> values = new ArrayList<>(before);
        context.getBindings(ID).putMember("a", new ArrayPrototypeInteropTest.MyProxyArray(values));
        Value resultValue = context.eval(ID, test);
        List<Integer> afterValue = new ArrayList<>(context.getBindings(ID).getMember("a").as(LIST_OF_INTEGER));
        assertEquals("array", afterExpected, afterValue);
        resultTest.accept(resultValue);
    }
}
