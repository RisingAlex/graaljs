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
package com.oracle.truffle.js.runtime.interop;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.interop.ExportValueNode;
import com.oracle.truffle.js.nodes.interop.ImportValueNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSAbstractArray;
import com.oracle.truffle.js.runtime.builtins.JSArrayBuffer;
import com.oracle.truffle.js.runtime.builtins.JSArrayBufferObject;
import com.oracle.truffle.js.runtime.builtins.JSError;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.PropertyDescriptor;
import com.oracle.truffle.js.runtime.objects.Undefined;

/**
 * Utility class for interop operations. Provides methods that can be used in Cached annotations of
 * the TruffleDSL to create interop nodes just for specific specializations.
 *
 */
public final class JSInteropUtil {
    private JSInteropUtil() {
        // this class should not be instantiated
    }

    public static long getArraySize(Object foreignObj, InteropLibrary interop, Node originatingNode) {
        try {
            return interop.getArraySize(foreignObj);
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorInteropException(foreignObj, e, "getArraySize", originatingNode);
        }
    }

    public static boolean setArraySize(Object obj, Object value, boolean isStrict, InteropLibrary interop, Node originatingNode, BranchProfile errorBranch) {
        long newLen = JSAbstractArray.toArrayLengthOrRangeError(value, originatingNode);
        long oldLen;
        try {
            oldLen = interop.getArraySize(obj);
        } catch (UnsupportedMessageException e) {
            if (errorBranch != null) {
                errorBranch.enter();
            }
            throw Errors.createTypeErrorInteropException(obj, e, "getArraySize", originatingNode);
        }
        String message = null;
        try {
            if (newLen < oldLen) {
                message = "removeArrayElement";
                for (long idx = oldLen - 1; idx >= newLen; idx--) {
                    interop.removeArrayElement(obj, idx);
                }
            } else {
                message = "writeArrayElement";
                for (long idx = oldLen; idx < newLen; idx++) {
                    interop.writeArrayElement(obj, idx, Undefined.instance);
                }
            }
        } catch (InteropException e) {
            if (isStrict) {
                if (errorBranch != null) {
                    errorBranch.enter();
                }
                throw Errors.createTypeErrorInteropException(obj, e, message, originatingNode);
            } else {
                return false;
            }
        }
        return true;
    }

    public static Object readMemberOrDefault(Object obj, Object member, Object defaultValue) {
        return readMemberOrDefault(obj, member, defaultValue, InteropLibrary.getUncached(), ImportValueNode.getUncached(), null);
    }

    public static Object readMemberOrDefault(Object obj, Object member, Object defaultValue, InteropLibrary interop, ImportValueNode importValue, Node originatingNode) {
        if (!Strings.isTString(member)) {
            return defaultValue;
        }
        try {
            return importValue.executeWithTarget(interop.readMember(obj, Strings.toJavaString((TruffleString) member)));
        } catch (UnknownIdentifierException e) {
            return defaultValue;
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorInteropException(obj, e, "readMember", member, originatingNode);
        }
    }

    public static Object readArrayElementOrDefault(Object obj, long index, Object defaultValue, InteropLibrary interop, ImportValueNode importValue, Node originatingNode) {
        try {
            return importValue.executeWithTarget(interop.readArrayElement(obj, index));
        } catch (InvalidArrayIndexException e) {
            return defaultValue;
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorInteropException(obj, e, "readArrayElement", index, originatingNode);
        }
    }

    public static Object readArrayElementOrDefault(Object obj, long index, Object defaultValue) {
        return readArrayElementOrDefault(obj, index, defaultValue, InteropLibrary.getUncached(), ImportValueNode.getUncached(), null);
    }

    public static void writeMember(Object obj, Object member, Object value) {
        writeMember(obj, member, value, InteropLibrary.getUncached(), ExportValueNode.getUncached(), null);
    }

    public static void writeMember(Object obj, Object member, Object value, InteropLibrary interop, ExportValueNode exportValue, Node originatingNode) {
        if (!Strings.isTString(member)) {
            return;
        }
        try {
            interop.writeMember(obj, Strings.toJavaString((TruffleString) member), exportValue.execute(value));
        } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException e) {
            throw Errors.createTypeErrorInteropException(obj, e, "writeMember", member, originatingNode);
        }
    }

    /**
     * Converts foreign objects to JS primitive values, coercing all numbers to double precision.
     */
    @InliningCutoff
    public static Object toPrimitiveOrDefaultLossy(Object obj, Object defaultValue, InteropLibrary interop, Node originatingNode) {
        if (interop.isNull(obj)) {
            return Null.instance;
        }
        try {
            if (interop.isBoolean(obj)) {
                return interop.asBoolean(obj);
            } else if (interop.isString(obj)) {
                return Strings.interopAsTruffleString(obj, interop);
            } else if (interop.isNumber(obj)) {
                if (interop.fitsInInt(obj)) {
                    return interop.asInt(obj);
                } else if (interop.fitsInDouble(obj)) {
                    return interop.asDouble(obj);
                } else if (interop.fitsInLong(obj)) {
                    return (double) interop.asLong(obj);
                } else if (interop.fitsInBigInteger(obj)) {
                    return BigInt.doubleValueOf(interop.asBigInteger(obj));
                }
            }
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorUnboxException(obj, e, originatingNode);
        }
        return defaultValue;
    }

    /**
     * Converts a foreign object to a JS primitive value. Attempts to keep the precise numeric value
     * when converting foreign numbers, relevant for comparisons and ToString/ToBigInt conversion.
     * Returned BigInt values are marked as foreign so that they are handled correctly by subsequent
     * ToNumeric, ToNumber (i.e., coerced to double), or ToBigInt.
     */
    @InliningCutoff
    public static Object toPrimitiveOrDefaultLossless(Object obj, Object defaultValue, InteropLibrary interop, TruffleString.SwitchEncodingNode switchEncoding, Node originatingNode) {
        if (interop.isNull(obj)) {
            return Null.instance;
        }
        try {
            if (interop.isBoolean(obj)) {
                return interop.asBoolean(obj);
            } else if (interop.isString(obj)) {
                return Strings.interopAsTruffleString(obj, interop, switchEncoding);
            } else if (interop.isNumber(obj)) {
                if (interop.fitsInInt(obj)) {
                    return interop.asInt(obj);
                } else if (interop.fitsInLong(obj)) {
                    return interop.asLong(obj);
                } else if (interop.fitsInBigInteger(obj)) {
                    return BigInt.fromForeignBigInteger(interop.asBigInteger(obj));
                } else if (interop.fitsInDouble(obj)) {
                    return interop.asDouble(obj);
                }
            }
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorUnboxException(obj, e, originatingNode);
        }
        return defaultValue;
    }

    @TruffleBoundary
    public static List<Object> keys(Object obj) {
        try {
            Object keysObj = InteropLibrary.getUncached().getMembers(obj);
            InteropLibrary keysInterop = InteropLibrary.getUncached(keysObj);
            long size = keysInterop.getArraySize(keysObj);
            if (size < 0 || size >= Integer.MAX_VALUE) {
                throw Errors.createRangeErrorInvalidArrayLength();
            }
            List<Object> keys = new ArrayList<>((int) size);
            for (int i = 0; i < size; i++) {
                Object key = keysInterop.readArrayElement(keysObj, i);
                keys.add(Strings.interopAsTruffleString(key));
            }
            return keys;
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw Errors.createTypeErrorInteropException(obj, e, "readArrayElement", null);
        }
    }

    @TruffleBoundary
    public static boolean hasProperty(Object obj, Object key) {
        if (key instanceof TruffleString) {
            return InteropLibrary.getUncached().isMemberExisting(obj, Strings.toJavaString((TruffleString) key));
        } else {
            return false;
        }
    }

    @TruffleBoundary
    public static boolean remove(Object obj, Object key) {
        if (key instanceof TruffleString) {
            try {
                InteropLibrary.getUncached().removeMember(obj, Strings.toJavaString((TruffleString) key));
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "removeMember", key, null);
            }
            return true;
        } else {
            return false;
        }
    }

    @TruffleBoundary
    public static Object call(Object function, Object[] args) {
        Object[] exportedArgs = JSRuntime.exportValueArray(args);
        try {
            return JSRuntime.importValue(InteropLibrary.getUncached().execute(function, exportedArgs));
        } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
            throw Errors.createTypeErrorInteropException(function, e, "execute", null);
        }
    }

    @TruffleBoundary
    public static Object construct(Object target, Object[] args) {
        Object[] exportedArgs = JSRuntime.exportValueArray(args);
        try {
            return JSRuntime.importValue(InteropLibrary.getUncached().instantiate(target, exportedArgs));
        } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
            throw Errors.createTypeErrorInteropException(target, e, "instantiate", null);
        }
    }

    public static boolean isBoxedPrimitive(Object receiver, InteropLibrary interop) {
        return interop.isString(receiver) || interop.isNumber(receiver) || interop.isBoolean(receiver);
    }

    public static PropertyDescriptor getOwnProperty(Object object, TruffleString propertyKey) {
        return getOwnProperty(object, propertyKey, InteropLibrary.getUncached(), ImportValueNode.getUncached(), TruffleString.ReadCharUTF16Node.getUncached());
    }

    @InliningCutoff
    public static PropertyDescriptor getOwnProperty(Object object, TruffleString propertyKey, InteropLibrary interop, ImportValueNode importValueNode, TruffleString.ReadCharUTF16Node charAtNode) {
        try {
            String key = Strings.toJavaString(propertyKey);
            if (interop.hasMembers(object) && interop.isMemberExisting(object, key)) {
                PropertyDescriptor desc = getExistingMemberProperty(object, key, interop, importValueNode);
                if (desc != null) {
                    return desc;
                }
            }
            long index = JSRuntime.propertyNameToArrayIndex(propertyKey, charAtNode);
            if (JSRuntime.isArrayIndex(index) && interop.hasArrayElements(object)) {
                return getArrayElementProperty(object, index, interop, importValueNode);
            }
        } catch (InteropException iex) {
        }
        return null;
    }

    @InliningCutoff
    public static PropertyDescriptor getExistingMemberProperty(Object object, String key, InteropLibrary interop, ImportValueNode importValueNode) throws InteropException {
        assert interop.hasMembers(object) && interop.isMemberExisting(object, key);
        if (interop.isMemberReadable(object, key)) {
            return PropertyDescriptor.createData(
                            importValueNode.executeWithTarget(interop.readMember(object, key)),
                            !interop.isMemberInternal(object, key),
                            interop.isMemberWritable(object, key),
                            interop.isMemberRemovable(object, key));
        }
        return null;
    }

    @InliningCutoff
    public static PropertyDescriptor getArrayElementProperty(Object object, long index, InteropLibrary interop, ImportValueNode importValueNode) throws InteropException {
        assert interop.hasArrayElements(object) && JSRuntime.isArrayIndex(index);
        if (interop.isArrayElementExisting(object, index) && interop.isArrayElementReadable(object, index)) {
            return PropertyDescriptor.createData(
                            importValueNode.executeWithTarget(interop.readArrayElement(object, index)),
                            true,
                            interop.isArrayElementWritable(object, index),
                            interop.isArrayElementRemovable(object, index));
        }
        return null;
    }

    @TruffleBoundary
    public static String formatError(Object error, InteropLibrary interopExc, InteropLibrary interopStr) {
        if (interopExc.isException(error)) {
            try {
                String message = null;
                if (interopExc.hasExceptionMessage(error)) {
                    message = interopStr.asString(interopExc.getExceptionMessage(error));
                }
                StringBuilder sb = new StringBuilder();
                sb.append(Objects.requireNonNullElse(message, "Error"));

                if (interopExc.hasExceptionStackTrace(error)) {
                    Object stackTrace = interopExc.getExceptionStackTrace(error);
                    InteropLibrary interopST = InteropLibrary.getUncached(stackTrace);
                    long length = interopST.getArraySize(stackTrace);
                    for (long i = 0; i < length; i++) {
                        Object stackTraceElement = interopST.readArrayElement(stackTrace, i);
                        InteropLibrary interopSTE = InteropLibrary.getUncached(stackTraceElement);

                        String name = "";
                        SourceSection sourceLocation = null;
                        if (interopSTE.hasExecutableName(stackTraceElement)) {
                            name = interopStr.asString(interopSTE.getExecutableName(stackTraceElement));
                        }
                        if (interopSTE.hasSourceLocation(stackTraceElement)) {
                            sourceLocation = interopSTE.getSourceLocation(stackTraceElement);
                        }

                        String className = "";
                        if (interopSTE.hasDeclaringMetaObject(stackTraceElement)) {
                            Object metaObject = interopSTE.getDeclaringMetaObject(stackTraceElement);
                            className = interopStr.asString(InteropLibrary.getUncached(metaObject).getMetaQualifiedName(metaObject));
                        }

                        if (name.isEmpty() && sourceLocation == null) {
                            continue;
                        }

                        sb.append('\n');
                        sb.append("    at ");
                        if (!className.isEmpty()) {
                            sb.append(className).append('.');
                        }
                        if (!name.isEmpty()) {
                            sb.append(name);
                        } else {
                            sb.append(JSError.ANONYMOUS_FUNCTION_NAME);
                        }
                        if (sourceLocation != null) {
                            sb.append(" (").append(formatSourceLocation(sourceLocation)).append(")");
                        }
                    }
                }
                return sb.toString();
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                assert false : e;
            }
        }

        return JSRuntime.safeToString(error).toString();
    }

    private static String formatSourceLocation(SourceSection sourceSection) {
        if (sourceSection == null) {
            return "Unknown";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(sourceSection.getSource().getName());

        sb.append(":");
        sb.append(sourceSection.getStartLine());
        if (sourceSection.getStartLine() < sourceSection.getEndLine()) {
            sb.append("-").append(sourceSection.getEndLine());
        }
        sb.append(":");
        sb.append(sourceSection.getCharIndex());
        if (sourceSection.getCharLength() > 1) {
            sb.append("-").append(sourceSection.getCharEndIndex() - 1);
        }
        return sb.toString();
    }

    public static ByteBuffer wasmMemoryAsByteBuffer(JSArrayBufferObject interopArrayBuffer, InteropLibrary interop, JSRealm realm) {
        assert JSArrayBuffer.isJSInteropArrayBuffer(interopArrayBuffer);
        Object memAsByteBuffer = realm.getWASMMemAsByteBuffer();
        if (memAsByteBuffer == null) {
            return null;
        }
        try {
            Object interopBuffer = JSArrayBuffer.getInteropBuffer(interopArrayBuffer);
            if (interopBuffer == null) {
                assert JSArrayBuffer.isDetachedBuffer(interopArrayBuffer);
                return null;
            }
            Object bufferObject = interop.execute(memAsByteBuffer, interopBuffer);
            TruffleLanguage.Env env = realm.getEnv();
            if (env.isHostObject(bufferObject)) {
                Object buffer = env.asHostObject(bufferObject);
                if (buffer instanceof ByteBuffer) {
                    return (ByteBuffer) buffer;
                }
            }
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
        return null;
    }

}
