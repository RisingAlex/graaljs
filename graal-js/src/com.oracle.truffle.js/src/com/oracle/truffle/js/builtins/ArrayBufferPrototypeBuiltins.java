/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.builtins;

import java.nio.ByteBuffer;
import java.util.EnumSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.js.builtins.ArrayBufferPrototypeBuiltinsFactory.ByteLengthGetterNodeGen;
import com.oracle.truffle.js.builtins.ArrayBufferPrototypeBuiltinsFactory.DetachedGetterNodeGen;
import com.oracle.truffle.js.builtins.ArrayBufferPrototypeBuiltinsFactory.JSArrayBufferSliceNodeGen;
import com.oracle.truffle.js.builtins.ArrayBufferPrototypeBuiltinsFactory.JSArrayBufferTransferNodeGen;
import com.oracle.truffle.js.builtins.ArrayPrototypeBuiltins.ArraySpeciesConstructorNode;
import com.oracle.truffle.js.nodes.cast.JSToIndexNode;
import com.oracle.truffle.js.nodes.cast.JSToIntegerAsLongNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSArrayBuffer;
import com.oracle.truffle.js.runtime.builtins.JSArrayBufferObject;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.DirectByteBufferHelper;

/**
 * Contains builtins for {@linkplain JSArrayBuffer}.prototype.
 */
public final class ArrayBufferPrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<ArrayBufferPrototypeBuiltins.ArrayBufferPrototype> {

    public static final JSBuiltinsContainer BUILTINS = new ArrayBufferPrototypeBuiltins();

    protected ArrayBufferPrototypeBuiltins() {
        super(JSArrayBuffer.PROTOTYPE_NAME, ArrayBufferPrototype.class);
    }

    public enum ArrayBufferPrototype implements BuiltinEnum<ArrayBufferPrototype> {
        byteLength(0),
        slice(2),

        // arraybuffer-transfer proposal
        detached(0),
        transfer(0),
        transferToFixedLength(0);

        private final int length;

        ArrayBufferPrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }

        @Override
        public boolean isGetter() {
            return this == byteLength || this == detached;
        }

        @Override
        public int getECMAScriptVersion() {
            if (EnumSet.of(detached, transfer, transferToFixedLength).contains(this)) {
                return JSConfig.StagingECMAScriptVersion;
            }
            return BuiltinEnum.super.getECMAScriptVersion();
        }

    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, ArrayBufferPrototype builtinEnum) {
        switch (builtinEnum) {
            case slice:
                return JSArrayBufferSliceNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case byteLength:
                return ByteLengthGetterNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case detached:
                return DetachedGetterNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case transfer:
                return JSArrayBufferTransferNodeGen.create(context, builtin, true, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case transferToFixedLength:
                return JSArrayBufferTransferNodeGen.create(context, builtin, false, args().withThis().fixedArgs(1).createArgumentNodes(context));
        }
        return null;
    }

    @ImportStatic({JSArrayBuffer.class, JSConfig.class})
    public abstract static class ByteLengthGetterNode extends JSBuiltinNode {

        public ByteLengthGetterNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(guards = "isJSHeapArrayBuffer(thisObj)")
        protected int heapArrayBuffer(Object thisObj) {
            byte[] byteArray = JSArrayBuffer.getByteArray(thisObj);
            if (!getContext().getTypedArrayNotDetachedAssumption().isValid() && byteArray == null) {
                return 0;
            }
            return byteArray.length;
        }

        @Specialization(guards = "isJSDirectArrayBuffer(thisObj)")
        protected int directArrayBuffer(Object thisObj) {
            ByteBuffer byteBuffer = JSArrayBuffer.getDirectByteBuffer(thisObj);
            if (!getContext().getTypedArrayNotDetachedAssumption().isValid() && byteBuffer == null) {
                return 0;
            }
            return byteBuffer.capacity();
        }

        @Specialization(guards = "isJSInteropArrayBuffer(thisObj)")
        protected int interopArrayBuffer(Object thisObj,
                        @CachedLibrary(limit = "InteropLibraryLimit") InteropLibrary interop) {
            Object buffer = JSArrayBuffer.getInteropBuffer(thisObj);
            if (!getContext().getTypedArrayNotDetachedAssumption().isValid() && buffer == null) {
                return 0;
            }
            try {
                long bufferSize = interop.getBufferSize(buffer);
                // Buffer size was already checked in the ArrayBuffer constructor.
                assert JSRuntime.longIsRepresentableAsInt(bufferSize);
                return (int) bufferSize;
            } catch (UnsupportedMessageException e) {
                return 0;
            }
        }

        @Fallback
        protected static int error(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeErrorArrayBufferExpected();
        }

    }

    public abstract static class JSArrayBufferOperation extends JSBuiltinNode {

        public JSArrayBufferOperation(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Child private JSToIntegerAsLongNode toIntegerNode;

        protected long toInteger(Object thisObject) {
            if (toIntegerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntegerNode = insert(JSToIntegerAsLongNode.create());
            }
            return toIntegerNode.executeLong(thisObject);
        }
    }

    public abstract static class JSArrayBufferAbstractSliceNode extends JSArrayBufferOperation {

        @Child private ArraySpeciesConstructorNode arraySpeciesCreateNode;

        public JSArrayBufferAbstractSliceNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        protected int getStart(Object start, int len) {
            long relativeStart = toInteger(start);
            if (relativeStart < 0) {
                return (int) Math.max((len + relativeStart), 0);
            } else {
                return (int) Math.min(relativeStart, len);
            }
        }

        protected int getEnd(Object end, int len) {
            long relativeEnd = end == Undefined.instance ? len : toInteger(end);
            if (relativeEnd < 0) {
                return (int) Math.max((len + relativeEnd), 0);
            } else {
                return (int) Math.min(relativeEnd, len);
            }
        }

        /**
         * Clamp index to range [lowerBound,upperBound]. A negative index refers from upperBound.
         */
        protected static int clampIndex(int index, int lowerBound, int upperBound) {
            return clamp(index >= 0 ? index : index + upperBound, lowerBound, upperBound);
        }

        /**
         * Clamp index to range [lowerBound,upperBound].
         */
        private static int clamp(int index, int lowerBound, int upperBound) {
            return Math.max(Math.min(index, upperBound), lowerBound);
        }

        public ArraySpeciesConstructorNode getArraySpeciesConstructorNode() {
            if (arraySpeciesCreateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                arraySpeciesCreateNode = insert(ArraySpeciesConstructorNode.create(getContext(), true));
            }
            return arraySpeciesCreateNode;
        }

    }

    @ImportStatic({JSArrayBuffer.class, JSConfig.class})
    public abstract static class JSArrayBufferSliceNode extends JSArrayBufferAbstractSliceNode {

        public JSArrayBufferSliceNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        /**
         * ArrayBuffer slice(long begin, optional long end).
         *
         * Returns a new ArrayBuffer whose contents are a copy of this ArrayBuffer's bytes from
         * begin, inclusive, up to end, exclusive. If either begin or end is negative, it refers to
         * an index from the end of the array, as opposed to from the beginning.
         *
         * If end is unspecified, the new ArrayBuffer contains all bytes from begin to the end of
         * this ArrayBuffer.
         *
         * The range specified by the begin and end values is clamped to the valid index range for
         * the current array. If the computed length of the new ArrayBuffer would be negative, it is
         * clamped to zero.
         *
         * @param thisObj ArrayBuffer
         * @param begin begin index
         * @param end end index
         * @return sliced ArrayBuffer
         */
        @Specialization
        protected JSDynamicObject sliceIntInt(JSArrayBufferObject.Heap thisObj, int begin, int end,
                        @Cached @Shared("errorBranch") InlinedBranchProfile errorBranch) {
            checkDetachedBuffer(thisObj, errorBranch);
            byte[] byteArray = JSArrayBuffer.getByteArray(thisObj);
            int clampedBegin = clampIndex(begin, 0, byteArray.length);
            int clampedEnd = clampIndex(end, clampedBegin, byteArray.length);
            int newLen = Math.max(clampedEnd - clampedBegin, 0);

            JSArrayBufferObject resObj = constructNewArrayBuffer(thisObj, newLen, false, errorBranch);

            byte[] newByteArray = JSArrayBuffer.getByteArray(resObj);
            System.arraycopy(byteArray, clampedBegin, newByteArray, 0, newLen);
            return resObj;
        }

        private JSArrayBufferObject constructNewArrayBuffer(JSArrayBufferObject thisObj, int newLen, boolean direct, InlinedBranchProfile errorBranch) {
            JSDynamicObject defaultConstructor = getRealm().getArrayBufferConstructor();
            var constr = getArraySpeciesConstructorNode().speciesConstructor(thisObj, defaultConstructor);
            var resObj = getArraySpeciesConstructorNode().construct(constr, newLen);
            if ((direct && !JSArrayBuffer.isJSDirectArrayBuffer(resObj)) || (!direct && !JSArrayBuffer.isJSHeapArrayBuffer(resObj))) {
                errorBranch.enter(this);
                throw Errors.createTypeErrorArrayBufferExpected();
            }
            var newBuffer = (JSArrayBufferObject) resObj;
            checkDetachedBuffer(newBuffer, errorBranch);
            if (resObj == thisObj) {
                errorBranch.enter(this);
                throw Errors.createTypeError("SameValue(new, O) is forbidden");
            }
            if ((direct && JSArrayBuffer.getDirectByteLength(resObj) < newLen) || (!direct && JSArrayBuffer.getHeapByteLength(resObj) < newLen)) {
                errorBranch.enter(this);
                throw Errors.createTypeError("insufficient length constructed");
            }
            // NOTE: Side-effects of the above steps may have detached O.
            // yes, check again! see clause 22 of ES 6 24.1.4.3.
            checkDetachedBuffer(thisObj, errorBranch);
            return newBuffer;
        }

        private void checkDetachedBuffer(JSArrayBufferObject arrayBuffer, InlinedBranchProfile errorBranch) {
            if (!getContext().getTypedArrayNotDetachedAssumption().isValid() && JSArrayBuffer.isDetachedBuffer(arrayBuffer)) {
                errorBranch.enter(this);
                throw Errors.createTypeErrorDetachedBuffer();
            }
        }

        @Specialization(replaces = "sliceIntInt")
        protected JSDynamicObject slice(JSArrayBufferObject.Heap thisObj, Object begin0, Object end0,
                        @Cached @Shared("errorBranch") InlinedBranchProfile errorBranch) {
            checkDetachedBuffer(thisObj, errorBranch);
            int len = JSArrayBuffer.getByteArray(thisObj).length;
            int begin = getStart(begin0, len);
            int finalEnd = getEnd(end0, len);
            return sliceIntInt(thisObj, begin, finalEnd, errorBranch);
        }

        @Specialization
        protected JSDynamicObject sliceDirectIntInt(JSArrayBufferObject.Direct thisObj, int begin, int end,
                        @Cached @Shared("errorBranch") InlinedBranchProfile errorBranch) {
            checkDetachedBuffer(thisObj, errorBranch);
            ByteBuffer byteBuffer = JSArrayBuffer.getDirectByteBuffer(thisObj);
            int byteLength = JSArrayBuffer.getDirectByteLength(thisObj);
            int clampedBegin = clampIndex(begin, 0, byteLength);
            int clampedEnd = clampIndex(end, clampedBegin, byteLength);
            int newLen = clampedEnd - clampedBegin;

            JSArrayBufferObject resObj = constructNewArrayBuffer(thisObj, newLen, true, errorBranch);

            ByteBuffer resBuffer = JSArrayBuffer.getDirectByteBuffer(resObj);
            Boundaries.byteBufferPutSlice(resBuffer, 0, byteBuffer, clampedBegin, clampedEnd);
            return resObj;
        }

        @Specialization(replaces = "sliceDirectIntInt")
        protected JSDynamicObject sliceDirect(JSArrayBufferObject.Direct thisObj, Object begin0, Object end0,
                        @Cached @Shared("errorBranch") InlinedBranchProfile errorBranch) {
            checkDetachedBuffer(thisObj, errorBranch);
            int len = JSArrayBuffer.getDirectByteLength(thisObj);
            int begin = getStart(begin0, len);
            int end = getEnd(end0, len);
            return sliceDirectIntInt(thisObj, begin, end, errorBranch);
        }

        @Specialization
        protected Object sliceInterop(JSArrayBufferObject.Interop thisObj, Object begin0, Object end0,
                        @Cached @Shared("errorBranch") InlinedBranchProfile errorBranch,
                        @CachedLibrary(limit = "InteropLibraryLimit") @Shared("srcBufferLib") InteropLibrary srcBufferLib,
                        @CachedLibrary(limit = "InteropLibraryLimit") @Shared("dstBufferLib") InteropLibrary dstBufferLib) {
            checkDetachedBuffer(thisObj, errorBranch);
            Object interopBuffer = JSArrayBuffer.getInteropBuffer(thisObj);
            int length = ConstructorBuiltins.ConstructArrayBufferNode.getBufferSizeSafe(interopBuffer, srcBufferLib, this, errorBranch);
            int begin = getStart(begin0, length);
            int end = getEnd(end0, length);
            int clampedBegin = clampIndex(begin, 0, length);
            int clampedEnd = clampIndex(end, clampedBegin, length);
            int newLen = Math.max(clampedEnd - clampedBegin, 0);

            JSArrayBufferObject resObj = constructNewArrayBuffer(thisObj, newLen, getContext().isOptionDirectByteBuffer(), errorBranch);

            copyInteropBufferElements(thisObj, resObj, clampedBegin, newLen, errorBranch, srcBufferLib, dstBufferLib);
            return resObj;
        }

        private void copyInteropBufferElements(Object srcBuffer, Object dstBuffer, int srcBufferOffset, int len,
                        InlinedBranchProfile errorBranch, InteropLibrary srcBufferLib, InteropLibrary dstBufferLib) {
            try {
                for (int i = 0; i < len; i++) {
                    dstBufferLib.writeBufferByte(dstBuffer, i, srcBufferLib.readBufferByte(srcBuffer, srcBufferOffset + i));
                }
            } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                errorBranch.enter(this);
                throw Errors.createTypeErrorInteropException(dstBuffer, e, "buffer access", null);
            }
        }

        @Specialization(guards = {"!isJSSharedArrayBuffer(thisObj)", "hasBufferElements(thisObj, srcBufferLib)"})
        protected Object sliceTruffleBuffer(Object thisObj, Object begin0, Object end0,
                        @Cached @Shared("errorBranch") InlinedBranchProfile errorBranch,
                        @CachedLibrary(limit = "InteropLibraryLimit") @Shared("srcBufferLib") InteropLibrary srcBufferLib,
                        @CachedLibrary(limit = "InteropLibraryLimit") @Shared("dstBufferLib") InteropLibrary dstBufferLib) {
            return sliceInterop(JSArrayBuffer.createInteropArrayBuffer(getContext(), getRealm(), thisObj), begin0, end0,
                            errorBranch, srcBufferLib, dstBufferLib);
        }

        @Fallback
        protected static JSDynamicObject error(Object thisObj, @SuppressWarnings("unused") Object begin0, @SuppressWarnings("unused") Object end0) {
            throw Errors.createTypeErrorIncompatibleReceiver(thisObj);
        }

        // Workaround for GR-29876
        static boolean hasBufferElements(Object buffer, InteropLibrary interop) {
            return interop.hasBufferElements(buffer);
        }
    }

    public abstract static class DetachedGetterNode extends JSBuiltinNode {

        public DetachedGetterNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(guards = {"!isJSSharedArrayBuffer(arrayBuffer)"})
        protected boolean detached(JSArrayBufferObject arrayBuffer) {
            if (getContext().getTypedArrayNotDetachedAssumption().isValid()) {
                return false;
            }
            return arrayBuffer.isDetached();
        }

        @Fallback
        protected static boolean error(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeErrorArrayBufferExpected();
        }

    }

    public abstract static class JSArrayBufferTransferNode extends JSBuiltinNode {
        private final boolean preserveResizability;

        public JSArrayBufferTransferNode(JSContext context, JSBuiltin builtin, boolean preserveResizability) {
            super(context, builtin);
            this.preserveResizability = preserveResizability;
        }

        @Specialization(guards = {"!isJSSharedArrayBuffer(arrayBuffer)"})
        protected Object transfer(JSArrayBufferObject arrayBuffer, Object newLength,
                        @Cached JSToIndexNode toIndexNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedBranchProfile differentByteLengthBranch,
                        @Cached InlinedConditionProfile heapBufferProfile,
                        @Cached InlinedConditionProfile directBufferProfile) {
            int newByteLength = 0;
            if (newLength != Undefined.instance) {
                long byteLength = toIndexNode.executeLong(newLength);
                if (byteLength > getContext().getLanguageOptions().maxTypedArrayLength()) {
                    errorBranch.enter(this);
                    throw Errors.createRangeErrorInvalidBufferSize();
                }
                newByteLength = (int) byteLength;
            } // else postpone the reading of byteLength after the detach check below

            if (!getContext().getTypedArrayNotDetachedAssumption().isValid() && JSArrayBuffer.isDetachedBuffer(arrayBuffer)) {
                errorBranch.enter(this);
                throw Errors.createTypeErrorDetachedBuffer();
            }

            int oldByteLength = getByteLength(arrayBuffer, heapBufferProfile, directBufferProfile);
            if (newLength == Undefined.instance) {
                newByteLength = oldByteLength;
            }

            @SuppressWarnings("unused")
            int newMaxByteLength;
            if (preserveResizability && arrayBuffer.isResizable()) {
                newMaxByteLength = arrayBuffer.getMaxByteLength();
            } else {
                newMaxByteLength = -1; // empty
            }

            if (arrayBuffer.getDetachKey() != Undefined.instance) {
                errorBranch.enter(this);
                throw Errors.createTypeErrorInvalidDetachKey();
            }

            boolean sameByteLength = (newByteLength == oldByteLength);
            int copyLength = Math.min(newByteLength, oldByteLength);
            JSContext context = getContext();
            JSRealm realm = getRealm();
            JSArrayBufferObject newBuffer;

            if (heapBufferProfile.profile(this, JSArrayBuffer.isJSHeapArrayBuffer(arrayBuffer))) {
                JSArrayBufferObject.Heap heapArrayBuffer = (JSArrayBufferObject.Heap) arrayBuffer;
                byte[] array = heapArrayBuffer.getByteArray();
                if (!sameByteLength) {
                    differentByteLengthBranch.enter(this);
                    byte[] newArray = new byte[newByteLength];
                    System.arraycopy(array, 0, newArray, 0, copyLength);
                    array = newArray;
                }
                newBuffer = JSArrayBuffer.createArrayBuffer(context, realm, array);
            } else if (directBufferProfile.profile(this, JSArrayBuffer.isJSDirectArrayBuffer(arrayBuffer))) {
                JSArrayBufferObject.Direct directArrayBuffer = (JSArrayBufferObject.Direct) arrayBuffer;
                ByteBuffer byteBuffer = directArrayBuffer.getByteBuffer();
                if (!sameByteLength) {
                    differentByteLengthBranch.enter(this);
                    ByteBuffer newByteBuffer = DirectByteBufferHelper.allocateDirect(newByteLength);
                    Boundaries.byteBufferPutSlice(newByteBuffer, 0, byteBuffer, 0, copyLength);
                    byteBuffer = newByteBuffer;
                }
                newBuffer = JSArrayBuffer.createDirectArrayBuffer(context, realm, byteBuffer);
            } else {
                assert JSArrayBuffer.isJSInteropArrayBuffer(arrayBuffer);
                throw Errors.createTypeError("Cannot transfer an interop ArrayBuffer");
            }

            JSArrayBuffer.detachArrayBuffer(arrayBuffer);

            return newBuffer;
        }

        private int getByteLength(JSArrayBufferObject arrayBuffer, InlinedConditionProfile heapBufferProfile, InlinedConditionProfile directBufferProfile) {
            if (heapBufferProfile.profile(this, JSArrayBuffer.isJSHeapArrayBuffer(arrayBuffer))) {
                return JSArrayBuffer.getHeapByteLength(arrayBuffer);
            } else if (directBufferProfile.profile(this, JSArrayBuffer.isJSDirectArrayBuffer(arrayBuffer))) {
                return JSArrayBuffer.getDirectByteLength(arrayBuffer);
            } else {
                return ((JSArrayBufferObject.Interop) arrayBuffer).getByteLength();
            }
        }

        @Fallback
        protected static Object error(@SuppressWarnings("unused") Object thisObj, @SuppressWarnings("unused") Object newLength) {
            throw Errors.createTypeErrorArrayBufferExpected();
        }

    }

}
