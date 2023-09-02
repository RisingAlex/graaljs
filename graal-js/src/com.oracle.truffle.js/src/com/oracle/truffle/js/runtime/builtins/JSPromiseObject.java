/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.runtime.builtins;

import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSNonProxyObject;
import com.oracle.truffle.js.runtime.objects.PromiseReactionRecord;
import com.oracle.truffle.js.runtime.util.SimpleArrayList;

public final class JSPromiseObject extends JSNonProxyObject {
    private int promiseState;
    private boolean promiseIsHandled;
    private Object promiseResult;
    private SimpleArrayList<PromiseReactionRecord> promiseFulfillReactions;
    private SimpleArrayList<PromiseReactionRecord> promiseRejectReactions;

    protected JSPromiseObject(Shape shape, JSDynamicObject proto, int promiseState) {
        super(shape, proto);
        this.promiseState = promiseState;
    }

    public int getPromiseState() {
        return promiseState;
    }

    public void setPromiseState(int promiseState) {
        this.promiseState = promiseState;
    }

    public boolean isHandled() {
        return promiseIsHandled;
    }

    public void setIsHandled(boolean handled) {
        this.promiseIsHandled = handled;
    }

    public Object getPromiseResult() {
        return promiseResult;
    }

    public void setPromiseResult(Object promiseResult) {
        this.promiseResult = promiseResult;
    }

    public SimpleArrayList<PromiseReactionRecord> getPromiseFulfillReactions() {
        return promiseFulfillReactions;
    }

    public SimpleArrayList<PromiseReactionRecord> getPromiseRejectReactions() {
        return promiseRejectReactions;
    }

    public void allocatePromiseReactions() {
        promiseFulfillReactions = new SimpleArrayList<>();
        promiseRejectReactions = new SimpleArrayList<>();
    }

    public void clearPromiseReactions() {
        promiseFulfillReactions = null;
        promiseRejectReactions = null;
    }

    public static JSPromiseObject create(Shape shape, JSDynamicObject proto, int promiseState) {
        return new JSPromiseObject(shape, proto, promiseState);
    }
}
