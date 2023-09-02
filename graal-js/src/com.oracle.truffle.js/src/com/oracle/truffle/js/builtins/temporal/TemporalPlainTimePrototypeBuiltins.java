/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.builtins.temporal;

import static com.oracle.truffle.js.runtime.util.TemporalConstants.AUTO;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.HALF_EXPAND;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.HOUR;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MICROSECOND;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MILLISECOND;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MINUTE;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.NANOSECOND;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.PLAIN_DATE;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.SECOND;
import static com.oracle.truffle.js.runtime.util.TemporalUtil.dtoi;
import static com.oracle.truffle.js.runtime.util.TemporalUtil.dtol;

import java.util.EnumSet;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.builtins.JSBuiltinsContainer;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeAddNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeEqualsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeGetISOFieldsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeGetterNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeRoundNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeSinceNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeSubtractNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeToLocaleStringNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeToPlainDateTimeNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeToStringNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeToZonedDateTimeNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeUntilNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeValueOfNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainTimePrototypeBuiltinsFactory.JSTemporalPlainTimeWithNodeGen;
import com.oracle.truffle.js.nodes.access.EnumerableOwnPropertyNamesNode;
import com.oracle.truffle.js.nodes.cast.JSToIntegerAsIntNode;
import com.oracle.truffle.js.nodes.cast.JSToIntegerThrowOnInfinityNode;
import com.oracle.truffle.js.nodes.cast.JSToNumberNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.temporal.TemporalGetOptionNode;
import com.oracle.truffle.js.nodes.temporal.TemporalRoundDurationNode;
import com.oracle.truffle.js.nodes.temporal.ToLimitedTemporalDurationNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalDateNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalTimeNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalTimeZoneNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSOrdinary;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDuration;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDurationObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDurationRecord;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalInstantObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateTime;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateTimeObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainTime;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainTimeObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPrecisionRecord;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTime;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTimeObject;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalConstants;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;
import com.oracle.truffle.js.runtime.util.TemporalUtil.Disambiguation;
import com.oracle.truffle.js.runtime.util.TemporalUtil.Overflow;
import com.oracle.truffle.js.runtime.util.TemporalUtil.RoundingMode;
import com.oracle.truffle.js.runtime.util.TemporalUtil.Unit;

public class TemporalPlainTimePrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<TemporalPlainTimePrototypeBuiltins.TemporalPlainTimePrototype> {

    public static final JSBuiltinsContainer BUILTINS = new TemporalPlainTimePrototypeBuiltins();

    protected TemporalPlainTimePrototypeBuiltins() {
        super(JSTemporalPlainTime.PROTOTYPE_NAME, TemporalPlainTimePrototype.class);
    }

    public enum TemporalPlainTimePrototype implements BuiltinEnum<TemporalPlainTimePrototype> {
        // getters
        calendar(0),
        hour(0),
        minute(0),
        second(0),
        millisecond(0),
        microsecond(0),
        nanosecond(0),

        // methods
        add(1),
        subtract(1),
        with(1),
        until(1),
        since(1),
        round(1),
        equals(1),
        toPlainDateTime(1),
        toZonedDateTime(1),
        getISOFields(0),
        toString(0),
        toLocaleString(0),
        toJSON(0),
        valueOf(0);

        private final int length;

        TemporalPlainTimePrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }

        @Override
        public boolean isGetter() {
            return EnumSet.of(calendar, hour, minute, second, millisecond, microsecond, nanosecond).contains(this);
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, TemporalPlainTimePrototype builtinEnum) {
        switch (builtinEnum) {
            case calendar:
            case hour:
            case minute:
            case second:
            case millisecond:
            case microsecond:
            case nanosecond:
                return JSTemporalPlainTimeGetterNodeGen.create(context, builtin, builtinEnum, args().withThis().createArgumentNodes(context));
            case add:
                return JSTemporalPlainTimeAddNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case subtract:
                return JSTemporalPlainTimeSubtractNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case with:
                return JSTemporalPlainTimeWithNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case until:
                return JSTemporalPlainTimeUntilNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case since:
                return JSTemporalPlainTimeSinceNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case round:
                return JSTemporalPlainTimeRoundNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case equals:
                return JSTemporalPlainTimeEqualsNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case toPlainDateTime:
                return JSTemporalPlainTimeToPlainDateTimeNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case toZonedDateTime:
                return JSTemporalPlainTimeToZonedDateTimeNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case getISOFields:
                return JSTemporalPlainTimeGetISOFieldsNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case toString:
                return JSTemporalPlainTimeToStringNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case toLocaleString:
            case toJSON:
                return JSTemporalPlainTimeToLocaleStringNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case valueOf:
                return JSTemporalPlainTimeValueOfNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class JSTemporalPlainTimeGetterNode extends JSBuiltinNode {

        public final TemporalPlainTimePrototype property;

        public JSTemporalPlainTimeGetterNode(JSContext context, JSBuiltin builtin, TemporalPlainTimePrototype property) {
            super(context, builtin);
            this.property = property;
        }

        @Specialization
        protected final Object timeGetter(JSTemporalPlainTimeObject temporalTime) {
            switch (property) {
                case calendar:
                    return temporalTime.getCalendar();
                case hour:
                    return temporalTime.getHour();
                case minute:
                    return temporalTime.getMinute();
                case second:
                    return temporalTime.getSecond();
                case millisecond:
                    return temporalTime.getMillisecond();
                case microsecond:
                    return temporalTime.getMicrosecond();
                case nanosecond:
                    return temporalTime.getNanosecond();
            }
            throw Errors.shouldNotReachHere();
        }

        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static Object invalidReceiver(@SuppressWarnings("unused") Object thisObj) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    public abstract static class PlainTimeOperation extends JSTemporalBuiltinOperation {
        public PlainTimeOperation(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        protected JSTemporalPlainTimeObject addDurationToOrSubtractDurationFromPlainTime(int sign, JSTemporalPlainTimeObject temporalTime, Object temporalDurationLike,
                        ToLimitedTemporalDurationNode toLimitedTemporalDurationNode,
                        Node node, InlinedBranchProfile errorBranch) {
            JSTemporalDurationRecord duration = toLimitedTemporalDurationNode.execute(temporalDurationLike, TemporalUtil.listEmpty);

            JSTemporalDurationRecord result = TemporalUtil.addTimeDouble(
                            temporalTime.getHour(), temporalTime.getMinute(), temporalTime.getSecond(),
                            temporalTime.getMillisecond(), temporalTime.getMicrosecond(), temporalTime.getNanosecond(),
                            sign * duration.getHours(), sign * duration.getMinutes(), sign * duration.getSeconds(),
                            sign * duration.getMilliseconds(), sign * duration.getMicroseconds(), sign * duration.getNanoseconds(),
                            node, errorBranch);
            assert TemporalUtil.isValidTime(dtoi(result.getHours()), dtoi(result.getMinutes()), dtoi(result.getSeconds()), dtoi(result.getMilliseconds()), dtoi(result.getMicroseconds()),
                            dtoi(result.getNanoseconds()));
            return JSTemporalPlainTime.create(getContext(), getRealm(),
                            dtoi(result.getHours()), dtoi(result.getMinutes()), dtoi(result.getSeconds()), dtoi(result.getMilliseconds()), dtoi(result.getMicroseconds()),
                            dtoi(result.getNanoseconds()),
                            node, errorBranch);
        }

        protected JSTemporalDurationObject differenceTemporalPlainTime(int sign, JSTemporalPlainTimeObject temporalTime, Object otherObj, Object optionsParam, JSToNumberNode toNumber,
                        EnumerableOwnPropertyNamesNode namesNode, ToTemporalTimeNode toTemporalTime, TruffleString.EqualNode equalNode, TemporalRoundDurationNode roundDurationNode,
                        TemporalGetOptionNode getOptionNode,
                        Node node, InlinedBranchProfile errorBranch, InlinedConditionProfile optionUndefined) {
            JSTemporalPlainTimeObject other = toTemporalTime.execute(otherObj, null);
            JSDynamicObject options = getOptionsObject(optionsParam, node, errorBranch, optionUndefined);
            Unit smallestUnit = toSmallestTemporalUnit(options, TemporalUtil.listYMWD, NANOSECOND, equalNode, getOptionNode, node, errorBranch);
            Unit largestUnit = toLargestTemporalUnit(options, TemporalUtil.listYMWD, AUTO, Unit.HOUR, equalNode, getOptionNode, node, errorBranch);
            TemporalUtil.validateTemporalUnitRange(largestUnit, smallestUnit);
            RoundingMode roundingMode = toTemporalRoundingMode(options, TemporalConstants.TRUNC, equalNode, getOptionNode);
            if (sign == TemporalUtil.SINCE) {
                roundingMode = TemporalUtil.negateTemporalRoundingMode(roundingMode);
            }
            Double maximum = TemporalUtil.maximumTemporalDurationRoundingIncrement(smallestUnit);
            long roundingIncrement = (long) TemporalUtil.toTemporalRoundingIncrement(options, maximum, false, isObjectNode, toNumber);
            JSTemporalDurationRecord result = TemporalUtil.differenceTime(
                            temporalTime.getHour(), temporalTime.getMinute(), temporalTime.getSecond(), temporalTime.getMillisecond(), temporalTime.getMicrosecond(),
                            temporalTime.getNanosecond(),
                            other.getHour(), other.getMinute(), other.getSecond(), other.getMillisecond(), other.getMicrosecond(), other.getNanosecond());
            JSTemporalDurationRecord result2 = roundDurationNode.execute(0, 0, 0, 0,
                            result.getHours(), result.getMinutes(), result.getSeconds(), result.getMilliseconds(), result.getMicroseconds(),
                            result.getNanoseconds(), roundingIncrement, smallestUnit, roundingMode, Undefined.instance);
            JSRealm realm = getRealm();
            JSTemporalDurationRecord result3 = TemporalUtil.balanceDuration(getContext(), realm, namesNode,
                            0, result2.getHours(), result2.getMinutes(), result2.getSeconds(), result2.getMilliseconds(), result2.getMicroseconds(),
                            result2.getNanoseconds(), largestUnit);
            return JSTemporalDuration.createTemporalDuration(getContext(), realm, 0, 0, 0, 0,
                            sign * result3.getHours(), sign * result3.getMinutes(), sign * result3.getSeconds(), sign * result3.getMilliseconds(), sign * result3.getMicroseconds(),
                            sign * result3.getNanoseconds(), node, errorBranch);
        }
    }

    public abstract static class JSTemporalPlainTimeAdd extends PlainTimeOperation {

        protected JSTemporalPlainTimeAdd(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalPlainTimeObject add(JSTemporalPlainTimeObject temporalTime, Object temporalDurationLike,
                        @Cached ToLimitedTemporalDurationNode toLimitedTemporalDurationNode,
                        @Cached InlinedBranchProfile errorBranch) {
            return addDurationToOrSubtractDurationFromPlainTime(TemporalUtil.ADD, temporalTime, temporalDurationLike, toLimitedTemporalDurationNode, this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSTemporalPlainTimeObject invalidReceiver(Object thisObj, Object temporalDurationLike) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    public abstract static class JSTemporalPlainTimeSubtract extends PlainTimeOperation {

        protected JSTemporalPlainTimeSubtract(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalPlainTimeObject subtract(JSTemporalPlainTimeObject temporalTime, Object temporalDurationLike,
                        @Cached ToLimitedTemporalDurationNode toLimitedTemporalDurationNode,
                        @Cached InlinedBranchProfile errorBranch) {
            return addDurationToOrSubtractDurationFromPlainTime(TemporalUtil.SUBTRACT, temporalTime, temporalDurationLike, toLimitedTemporalDurationNode, this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSTemporalPlainTimeObject invalidReceiver(Object thisObj, Object temporalDurationLike) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    // 4.3.12
    public abstract static class JSTemporalPlainTimeWith extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeWith(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected JSTemporalPlainTimeObject with(JSTemporalPlainTimeObject temporalTime, Object temporalTimeLike, Object options,
                        @Cached JSToIntegerThrowOnInfinityNode toIntThrows,
                        @Cached JSToIntegerAsIntNode toInt,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            if (!isObject(temporalTimeLike)) {
                errorBranch.enter(this);
                throw Errors.createTypeError("Temporal.Time like object expected.");
            }
            JSDynamicObject timeLikeObj = (JSDynamicObject) temporalTimeLike;
            TemporalUtil.rejectTemporalCalendarType(timeLikeObj, this, errorBranch);
            Object calendarProperty = JSObject.get(timeLikeObj, TemporalConstants.CALENDAR);
            if (calendarProperty != Undefined.instance) {
                errorBranch.enter(this);
                throw TemporalErrors.createTypeErrorUnexpectedCalendar();
            }
            Object timeZoneProperty = JSObject.get(timeLikeObj, TemporalConstants.TIME_ZONE);
            if (timeZoneProperty != Undefined.instance) {
                errorBranch.enter(this);
                throw TemporalErrors.createTypeErrorUnexpectedTimeZone();
            }
            JSDynamicObject partialTime = JSTemporalPlainTime.toPartialTime(timeLikeObj, isObjectNode, toIntThrows, getContext());
            JSDynamicObject normalizedOptions = getOptionsObject(options, this, errorBranch, optionUndefined);
            Overflow overflow = TemporalUtil.toTemporalOverflow(normalizedOptions, getOptionNode);
            int hour;
            int minute;
            int second;
            int millisecond;
            int microsecond;
            int nanosecond;
            Object tempValue = JSObject.get(partialTime, HOUR);
            if (tempValue != Undefined.instance) {
                hour = toInt.executeInt(tempValue);
            } else {
                hour = temporalTime.getHour();
            }
            tempValue = JSObject.get(partialTime, MINUTE);
            if (tempValue != Undefined.instance) {
                minute = toInt.executeInt(tempValue);
            } else {
                minute = temporalTime.getMinute();
            }
            tempValue = JSObject.get(partialTime, SECOND);
            if (tempValue != Undefined.instance) {
                second = toInt.executeInt(tempValue);
            } else {
                second = temporalTime.getSecond();
            }
            tempValue = JSObject.get(partialTime, MILLISECOND);
            if (tempValue != Undefined.instance) {
                millisecond = toInt.executeInt(tempValue);
            } else {
                millisecond = temporalTime.getMillisecond();
            }
            tempValue = JSObject.get(partialTime, MICROSECOND);
            if (tempValue != Undefined.instance) {
                microsecond = toInt.executeInt(tempValue);
            } else {
                microsecond = temporalTime.getMicrosecond();
            }
            tempValue = JSObject.get(partialTime, NANOSECOND);
            if (tempValue != Undefined.instance) {
                nanosecond = toInt.executeInt(tempValue);
            } else {
                nanosecond = temporalTime.getNanosecond();
            }
            JSTemporalDurationRecord result = TemporalUtil.regulateTime(hour, minute, second, millisecond, microsecond, nanosecond, overflow);
            return JSTemporalPlainTime.create(getContext(), getRealm(),
                            dtoi(result.getHours()), dtoi(result.getMinutes()), dtoi(result.getSeconds()), dtoi(result.getMilliseconds()), dtoi(result.getMicroseconds()),
                            dtoi(result.getNanoseconds()), this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSTemporalPlainTimeObject invalidReceiver(Object thisObj, Object temporalTimeLike, Object options) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    public abstract static class JSTemporalPlainTimeUntil extends PlainTimeOperation {

        protected JSTemporalPlainTimeUntil(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalDurationObject until(JSTemporalPlainTimeObject temporalTime, Object otherObj, Object optionsParam,
                        @Cached JSToNumberNode toNumber,
                        @Cached("createKeys(getContext())") EnumerableOwnPropertyNamesNode namesNode,
                        @Cached("create(getContext())") ToTemporalTimeNode toTemporalTime,
                        @Cached TruffleString.EqualNode equalNode, @Cached("create(getContext())") TemporalRoundDurationNode roundDurationNode,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            return differenceTemporalPlainTime(TemporalUtil.UNTIL, temporalTime, otherObj, optionsParam,
                            toNumber, namesNode, toTemporalTime, equalNode, roundDurationNode, getOptionNode, this, errorBranch, optionUndefined);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSTemporalDurationObject invalidReceiver(Object thisObj, Object otherObj, Object optionsParam) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    public abstract static class JSTemporalPlainTimeSince extends PlainTimeOperation {

        protected JSTemporalPlainTimeSince(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalDurationObject since(JSTemporalPlainTimeObject temporalTime, Object otherObj, Object optionsParam,
                        @Cached JSToNumberNode toNumber,
                        @Cached("createKeys(getContext())") EnumerableOwnPropertyNamesNode namesNode,
                        @Cached("create(getContext())") ToTemporalTimeNode toTemporalTime,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached("create(getContext())") TemporalRoundDurationNode roundDurationNode,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            return differenceTemporalPlainTime(TemporalUtil.SINCE, temporalTime, otherObj, optionsParam,
                            toNumber, namesNode, toTemporalTime, equalNode, roundDurationNode, getOptionNode, this, errorBranch, optionUndefined);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSTemporalDurationObject invalidReceiver(Object thisObj, Object otherObj, Object optionsParam) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    public abstract static class JSTemporalPlainTimeRound extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeRound(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected JSTemporalPlainTimeObject round(JSTemporalPlainTimeObject temporalTime, Object roundToParam,
                        @Cached JSToNumberNode toNumber,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            if (roundToParam == Undefined.instance) {
                errorBranch.enter(this);
                throw TemporalErrors.createTypeErrorOptionsUndefined();
            }
            JSDynamicObject roundTo;
            if (Strings.isTString(roundToParam)) {
                roundTo = JSOrdinary.createWithNullPrototype(getContext());
                JSRuntime.createDataPropertyOrThrow(roundTo, TemporalConstants.SMALLEST_UNIT, JSRuntime.toStringIsString(roundToParam));
            } else {
                roundTo = getOptionsObject(roundToParam, this, errorBranch, optionUndefined);
            }
            Unit smallestUnit = toSmallestTemporalUnit(roundTo, TemporalUtil.listYMWD, null, equalNode, getOptionNode, this, errorBranch);
            if (smallestUnit == Unit.EMPTY) {
                errorBranch.enter(this);
                throw TemporalErrors.createRangeErrorSmallestUnitExpected();
            }
            RoundingMode roundingMode = toTemporalRoundingMode(roundTo, HALF_EXPAND, equalNode, getOptionNode);
            int maximum;
            if (smallestUnit == Unit.HOUR) {
                maximum = 24;
            } else if (smallestUnit == Unit.MINUTE || smallestUnit == Unit.SECOND) {
                maximum = 60;
            } else {
                maximum = 1000;
            }
            double roundingIncrement = TemporalUtil.toTemporalRoundingIncrement(roundTo, (double) maximum,
                            false, isObjectNode, toNumber);
            JSTemporalDurationRecord result = TemporalUtil.roundTime(temporalTime.getHour(), temporalTime.getMinute(),
                            temporalTime.getSecond(), temporalTime.getMillisecond(), temporalTime.getMicrosecond(),
                            temporalTime.getNanosecond(), roundingIncrement, smallestUnit, roundingMode, null);
            return JSTemporalPlainTime.create(getContext(), getRealm(),
                            dtoi(result.getHours()), dtoi(result.getMinutes()), dtoi(result.getSeconds()), dtoi(result.getMilliseconds()), dtoi(result.getMicroseconds()),
                            dtoi(result.getNanoseconds()), this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSTemporalPlainTimeObject invalidReceiver(Object thisObj, Object roundToParam) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    // 4.3.16
    public abstract static class JSTemporalPlainTimeEquals extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeEquals(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected boolean equalsOtherObj(JSTemporalPlainTimeObject thisTime, JSTemporalPlainTimeObject otherObj) {
            return equalsIntl(thisTime, otherObj);
        }

        @Specialization(guards = "!isJSTemporalPlainTime(other)")
        protected boolean equalsGeneric(JSTemporalPlainTimeObject thisTime, Object other,
                        @Cached("create(getContext())") ToTemporalTimeNode toTemporalTime) {
            JSTemporalPlainTimeObject otherTime = toTemporalTime.execute(other, null);
            return equalsIntl(thisTime, otherTime);
        }

        private static boolean equalsIntl(JSTemporalPlainTimeObject thisTime, JSTemporalPlainTimeObject otherTime) {
            if (thisTime.getHour() != otherTime.getHour()) {
                return false;
            }
            if (thisTime.getMinute() != otherTime.getMinute()) {
                return false;
            }
            if (thisTime.getSecond() != otherTime.getSecond()) {
                return false;
            }
            if (thisTime.getMillisecond() != otherTime.getMillisecond()) {
                return false;
            }
            if (thisTime.getMicrosecond() != otherTime.getMicrosecond()) {
                return false;
            }
            if (thisTime.getNanosecond() != otherTime.getNanosecond()) {
                return false;
            }
            return true;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static boolean invalidReceiver(Object thisObj, Object otherObj) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    // 4.3.17
    public abstract static class JSTemporalPlainTimeToPlainDateTime extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeToPlainDateTime(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected final JSTemporalPlainDateTimeObject toPlainDateTime(JSTemporalPlainTimeObject time, Object temporalDateObj,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate,
                        @Cached InlinedBranchProfile errorBranch) {
            JSTemporalPlainDateObject date = toTemporalDate.execute(temporalDateObj, Undefined.instance);

            return JSTemporalPlainDateTime.create(getContext(), getRealm(),
                            date.getYear(), date.getMonth(), date.getDay(),
                            time.getHour(), time.getMinute(), time.getSecond(), time.getMillisecond(), time.getMicrosecond(), time.getNanosecond(), date.getCalendar(), this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSTemporalPlainDateTimeObject invalidReceiver(Object thisObj, Object temporalDateObj) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    // 4.3.18
    public abstract static class JSTemporalPlainTimeToZonedDateTime extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeToZonedDateTime(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected final JSTemporalZonedDateTimeObject toZonedDateTime(JSTemporalPlainTimeObject time, Object itemParam,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate,
                        @Cached("create(getContext())") ToTemporalTimeZoneNode toTemporalTimeZone,
                        @Cached InlinedBranchProfile errorBranch) {
            if (!JSRuntime.isObject(itemParam)) {
                throw Errors.createTypeErrorNotAnObject(itemParam);
            }
            JSDynamicObject item = (JSDynamicObject) itemParam;

            Object temporalDateLike = JSObject.get(item, PLAIN_DATE);
            if (temporalDateLike == Undefined.instance) {
                errorBranch.enter(this);
                throw TemporalErrors.createTypeErrorTemporalPlainDateExpected();
            }
            JSTemporalPlainDateObject date = toTemporalDate.execute(temporalDateLike, Undefined.instance);
            Object temporalTimeZoneLike = JSObject.get(item, TemporalConstants.TIME_ZONE);
            if (temporalTimeZoneLike == Undefined.instance || temporalTimeZoneLike == null) {
                errorBranch.enter(this);
                throw Errors.createTypeError("TimeZone expected");
            }
            JSDynamicObject timeZone = toTemporalTimeZone.execute(temporalTimeZoneLike);

            JSTemporalPlainDateTimeObject temporalDateTime = JSTemporalPlainDateTime.create(getContext(), getRealm(),
                            date.getYear(), date.getMonth(), date.getDay(),
                            time.getHour(), time.getMinute(), time.getSecond(), time.getMillisecond(), time.getMicrosecond(),
                            time.getNanosecond(), date.getCalendar(), this, errorBranch);
            JSTemporalInstantObject instant = TemporalUtil.builtinTimeZoneGetInstantFor(getContext(), getRealm(), timeZone, temporalDateTime, Disambiguation.COMPATIBLE);
            return JSTemporalZonedDateTime.create(getContext(), getRealm(), instant.getNanoseconds(), timeZone, date.getCalendar());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSTemporalZonedDateTimeObject invalidReceiver(Object thisObj, Object itemParam) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    // 4.3.19
    public abstract static class JSTemporalPlainTimeGetISOFields extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeGetISOFields(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected final JSObject getISOFields(JSTemporalPlainTimeObject time) {
            JSObject fields = JSOrdinary.create(getContext(), getRealm());
            TemporalUtil.createDataPropertyOrThrow(getContext(), fields, TemporalConstants.CALENDAR, time.getCalendar());
            TemporalUtil.createDataPropertyOrThrow(getContext(), fields, TemporalConstants.ISO_HOUR, time.getHour());
            TemporalUtil.createDataPropertyOrThrow(getContext(), fields, TemporalConstants.ISO_MICROSECOND, time.getMicrosecond());
            TemporalUtil.createDataPropertyOrThrow(getContext(), fields, TemporalConstants.ISO_MILLISECOND, time.getMillisecond());
            TemporalUtil.createDataPropertyOrThrow(getContext(), fields, TemporalConstants.ISO_MINUTE, time.getMinute());
            TemporalUtil.createDataPropertyOrThrow(getContext(), fields, TemporalConstants.ISO_NANOSECOND, time.getNanosecond());
            TemporalUtil.createDataPropertyOrThrow(getContext(), fields, TemporalConstants.ISO_SECOND, time.getSecond());
            return fields;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static JSObject invalidReceiver(Object thisObj) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    // 4.3.20
    public abstract static class JSTemporalPlainTimeToString extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeToString(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected final TruffleString toString(JSTemporalPlainTimeObject time, Object optionsParam,
                        @Cached JSToStringNode toStringNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            JSDynamicObject options = getOptionsObject(optionsParam, this, errorBranch, optionUndefined);
            JSTemporalPrecisionRecord precision = TemporalUtil.toSecondsStringPrecision(options, toStringNode, getOptionNode, equalNode);
            RoundingMode roundingMode = toTemporalRoundingMode(options, TemporalConstants.TRUNC, equalNode, getOptionNode);
            JSTemporalDurationRecord roundResult = TemporalUtil.roundTime(
                            time.getHour(), time.getMinute(), time.getSecond(),
                            time.getMillisecond(), time.getMicrosecond(), time.getNanosecond(),
                            precision.getIncrement(), precision.getUnit(), roundingMode,
                            null);
            return JSTemporalPlainTime.temporalTimeToString(
                            dtol(roundResult.getHours()), dtol(roundResult.getMinutes()), dtol(roundResult.getSeconds()), dtol(roundResult.getMilliseconds()), dtol(roundResult.getMicroseconds()),
                            dtol(roundResult.getNanoseconds()), precision.getPrecision());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static TruffleString invalidReceiver(Object thisObj, Object optionsParam) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    // 4.3.21
    // 4.3.22
    public abstract static class JSTemporalPlainTimeToLocaleString extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeToLocaleString(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected static TruffleString toLocaleString(JSTemporalPlainTimeObject time) {
            return JSTemporalPlainTime.temporalTimeToString(
                            time.getHour(), time.getMinute(), time.getSecond(),
                            time.getMillisecond(), time.getMicrosecond(), time.getNanosecond(),
                            TemporalConstants.AUTO);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalPlainTime(thisObj)")
        protected static TruffleString invalidReceiver(Object thisObj) {
            throw TemporalErrors.createTypeErrorTemporalPlainTimeExpected();
        }
    }

    // 4.3.23
    public abstract static class JSTemporalPlainTimeValueOf extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainTimeValueOf(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected static Object valueOf(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeError("Not supported.");
        }
    }
}
