/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.test.nashorn;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.test.JSTest;

public class JSAdapterTest {
    private static String testIntl(String sourceText) {
        try (Context context = JSTest.newContextBuilder().option(JSContextOptions.NASHORN_COMPATIBILITY_MODE_NAME, "true").allowAllAccess(true).build()) {
            Value result = context.eval(Source.newBuilder(JavaScriptLanguage.ID, sourceText, "jsadapter-test").buildLiteral());
            Assert.assertTrue(result.isString());
            return result.asString();
        }
    }

    @Test
    public void jsAdapterTest() {
        String sourceCode = "var msg='';\n" +
                        "var obj = new JSAdapter() {\n" +
                        "    __get__: function(name) { msg += '__get__'+name; },\n" +
                        "    __put__: function(name, value) { msg += '__put__'+name+value; },\n" +
                        "    __call__: function(name, arg1, arg2) { msg += '__call__'+arg1+arg2; },\n" +
                        "    __new__: function(arg1, arg2) { msg += '__new__'+arg1+arg2; },\n" +
                        "    __getKeys__: function() { msg += '__getKeys__'; return [ ]; },\n" +
                        "    __getValues__: function() { msg += '__getValues__'; return [ ]; },\n" +
                        "    __has__: function(name) { msg += '__has__'+name; return true; },\n" +
                        "    __delete__: function(name) { msg += '__delete__'+name; return true; },\n" +
                        "};\n" +
                        "obj.foo \n" +
                        "obj.foo = 42;\n" +
                        "obj.func('graal', '.js');\n" +
                        "new obj('Oracle Labs', 'GraalVM');\n" +
                        "for (i in obj) { };\n" +
                        "for each (i in obj) { };\n" +
                        "'test' in obj;\n" +
                        "delete obj.prop;\n" +
                        "msg;";

        Assert.assertEquals("__get__foo__put__foo42__call__graal.js__new__Oracle LabsGraalVM__getValues____has__test__delete__prop", testIntl(sourceCode));
    }

}