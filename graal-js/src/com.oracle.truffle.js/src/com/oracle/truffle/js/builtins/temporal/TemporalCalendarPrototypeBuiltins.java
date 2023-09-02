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
import static com.oracle.truffle.js.runtime.util.TemporalConstants.DAY;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.HOUR;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.ISO8601;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MICROSECOND;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MILLISECOND;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MINUTE;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MONTH;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.MONTH_CODE;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.NANOSECOND;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.SECOND;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.YEAR;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.builtins.JSBuiltinsContainer;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDateAddNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDateFromFieldsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDateUntilNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDayNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDayOfWeekNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDayOfYearNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDaysInMonthNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDaysInWeekNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarDaysInYearNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarFieldsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarGetterNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarInLeapYearNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarMergeFieldsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarMonthCodeNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarMonthDayFromFieldsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarMonthNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarMonthsInYearNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarWeekOfYearNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarYearMonthFromFieldsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalCalendarPrototypeBuiltinsFactory.JSTemporalCalendarYearNodeGen;
import com.oracle.truffle.js.nodes.access.EnumerableOwnPropertyNamesNode;
import com.oracle.truffle.js.nodes.access.GetIteratorNode;
import com.oracle.truffle.js.nodes.access.IteratorCloseNode;
import com.oracle.truffle.js.nodes.access.IteratorStepNode;
import com.oracle.truffle.js.nodes.access.IteratorValueNode;
import com.oracle.truffle.js.nodes.binary.JSIdenticalNode;
import com.oracle.truffle.js.nodes.cast.JSToIntegerOrInfinityNode;
import com.oracle.truffle.js.nodes.cast.JSToObjectNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.temporal.TemporalGetOptionNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalDateNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalDurationNode;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalCalendar;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalCalendarObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDateTimeRecord;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDuration;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDurationObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDurationRecord;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDate;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateTime;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainMonthDay;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainMonthDayObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainYearMonth;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainYearMonthObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalYearMonthDayRecord;
import com.oracle.truffle.js.runtime.builtins.temporal.TemporalMonth;
import com.oracle.truffle.js.runtime.builtins.temporal.TemporalYear;
import com.oracle.truffle.js.runtime.objects.IteratorRecord;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;
import com.oracle.truffle.js.runtime.util.TemporalUtil.Overflow;
import com.oracle.truffle.js.runtime.util.TemporalUtil.Unit;

public class TemporalCalendarPrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<TemporalCalendarPrototypeBuiltins.TemporalCalendarPrototype> {

    public static final TemporalCalendarPrototypeBuiltins BUILTINS = new TemporalCalendarPrototypeBuiltins();

    protected TemporalCalendarPrototypeBuiltins() {
        super(JSTemporalCalendar.PROTOTYPE_NAME, TemporalCalendarPrototype.class);
    }

    public enum TemporalCalendarPrototype implements BuiltinEnum<TemporalCalendarPrototype> {
        // getters
        id(0),

        // methods
        mergeFields(2),
        fields(1),
        dateFromFields(1),
        yearMonthFromFields(1),
        monthDayFromFields(1),
        dateAdd(2),
        dateUntil(2),
        year(1),
        month(1),
        monthCode(1),
        day(1),
        dayOfWeek(1),
        dayOfYear(1),
        weekOfYear(1),
        daysInWeek(1),
        daysInMonth(1),
        daysInYear(1),
        monthsInYear(1),
        inLeapYear(1),
        toString(0),
        toJSON(0);

        private final int length;

        TemporalCalendarPrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }

        @Override
        public boolean isGetter() {
            return EnumSet.of(id).contains(this);
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, TemporalCalendarPrototype builtinEnum) {
        switch (builtinEnum) {
            case id:
            case toString:
            case toJSON:
                return JSTemporalCalendarGetterNodeGen.create(context, builtin, builtinEnum, args().withThis().createArgumentNodes(context));

            case mergeFields:
                return JSTemporalCalendarMergeFieldsNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case fields:
                return JSTemporalCalendarFieldsNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case dateFromFields:
                return JSTemporalCalendarDateFromFieldsNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case yearMonthFromFields:
                return JSTemporalCalendarYearMonthFromFieldsNodeGen.create(context, builtin, args().withThis().fixedArgs(3).createArgumentNodes(context));
            case monthDayFromFields:
                return JSTemporalCalendarMonthDayFromFieldsNodeGen.create(context, builtin, args().withThis().fixedArgs(3).createArgumentNodes(context));
            case dateAdd:
                return JSTemporalCalendarDateAddNodeGen.create(context, builtin, args().withThis().fixedArgs(4).createArgumentNodes(context));
            case dateUntil:
                return JSTemporalCalendarDateUntilNodeGen.create(context, builtin, args().withThis().fixedArgs(3).createArgumentNodes(context));
            case year:
                return JSTemporalCalendarYearNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case month:
                return JSTemporalCalendarMonthNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case monthCode:
                return JSTemporalCalendarMonthCodeNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case day:
                return JSTemporalCalendarDayNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case dayOfWeek:
                return JSTemporalCalendarDayOfWeekNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case dayOfYear:
                return JSTemporalCalendarDayOfYearNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case daysInWeek:
                return JSTemporalCalendarDaysInWeekNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case weekOfYear:
                return JSTemporalCalendarWeekOfYearNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case daysInMonth:
                return JSTemporalCalendarDaysInMonthNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case daysInYear:
                return JSTemporalCalendarDaysInYearNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case monthsInYear:
                return JSTemporalCalendarMonthsInYearNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case inLeapYear:
                return JSTemporalCalendarInLeapYearNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class JSTemporalCalendarGetterNode extends JSBuiltinNode {

        protected final TemporalCalendarPrototype property;

        protected JSTemporalCalendarGetterNode(JSContext context, JSBuiltin builtin, TemporalCalendarPrototype property) {
            super(context, builtin);
            this.property = property;
        }

        @Specialization
        protected Object id(JSTemporalCalendarObject calendar,
                        @Cached JSToStringNode toStringNode) {
            switch (property) {
                case id:
                case toString:
                    return calendar.getId();
                case toJSON:
                    return toStringNode.executeString(calendar);
            }
            throw Errors.shouldNotReachHere();
        }

        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(@SuppressWarnings("unused") Object thisObj) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    public abstract static class JSTemporalCalendarMergeFields extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarMergeFields(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected JSDynamicObject mergeFields(JSTemporalCalendarObject calendar, Object fieldsParam, Object additionalFieldsParam,
                        @Cached JSToObjectNode toObject,
                        @Cached("createKeys(getContext())") EnumerableOwnPropertyNamesNode namesNode) {
            assert calendar.getId().equals(ISO8601);
            JSDynamicObject fields = (JSDynamicObject) toObject.execute(fieldsParam);
            JSDynamicObject additionalFields = (JSDynamicObject) toObject.execute(additionalFieldsParam);
            return TemporalUtil.defaultMergeFields(getContext(), getRealm(), fields, additionalFields, namesNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object fieldsParam, Object additionalFieldsParam) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    public abstract static class JSTemporalCalendarFields extends JSTemporalBuiltinOperation {
        @Child private IteratorCloseNode iteratorCloseNode;
        @Child private IteratorValueNode getIteratorValueNode;
        @Child private IteratorStepNode iteratorStepNode;

        protected void iteratorCloseAbrupt(Object iterator) {
            if (iteratorCloseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                iteratorCloseNode = insert(IteratorCloseNode.create(getContext()));
            }
            iteratorCloseNode.executeAbrupt(iterator);
        }

        protected Object getIteratorValue(Object iteratorResult) {
            if (getIteratorValueNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getIteratorValueNode = insert(IteratorValueNode.create());
            }
            return getIteratorValueNode.execute(iteratorResult);
        }

        protected Object iteratorStep(IteratorRecord iterator) {
            if (iteratorStepNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                iteratorStepNode = insert(IteratorStepNode.create());
            }
            return iteratorStepNode.execute(iterator);
        }

        protected JSTemporalCalendarFields(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected JSDynamicObject fields(JSTemporalCalendarObject calendar, Object fieldsParam,
                        @Cached(inline = true) GetIteratorNode getIteratorNode) {
            assert calendar.getId().equals(ISO8601);
            IteratorRecord iter = getIteratorNode.execute(this, fieldsParam /* , sync */);
            List<TruffleString> fieldNames = new ArrayList<>();
            Object next = Boolean.TRUE;
            while (next != Boolean.FALSE) {
                next = iteratorStep(iter);
                if (next != Boolean.FALSE) {
                    Object nextValue = getIteratorValue(next);
                    if (!Strings.isTString(nextValue)) {
                        iteratorCloseAbrupt(iter.getIterator());
                        throw Errors.createTypeError("string expected");
                    }
                    TruffleString str = JSRuntime.toString(nextValue);
                    if (str != null && Boundaries.listContains(fieldNames, str)) {
                        iteratorCloseAbrupt(iter.getIterator());
                        throw Errors.createRangeError("");
                    }
                    if (!(YEAR.equals(str) || MONTH.equals(str) || MONTH_CODE.equals(str) || DAY.equals(str) || HOUR.equals(str) ||
                                    MINUTE.equals(str) || SECOND.equals(str) || MILLISECOND.equals(str) || MICROSECOND.equals(str) || NANOSECOND.equals(str))) {
                        iteratorCloseAbrupt(iter.getIterator());
                        throw Errors.createRangeError("");
                    }
                    fieldNames.add(str);
                }
            }
            return JSRuntime.createArrayFromList(getContext(), getRealm(), fieldNames);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object fieldsParam) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.4
    public abstract static class JSTemporalCalendarDateFromFields extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarDateFromFields(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object dateFromFields(JSTemporalCalendarObject calendar, Object fields, Object optionsParam,
                        @Cached("createSameValue()") JSIdenticalNode identicalNode,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached JSToIntegerOrInfinityNode toIntOrInfinityNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            assert calendar.getId().equals(ISO8601);
            if (!isObject(fields)) {
                errorBranch.enter(this);
                throw TemporalErrors.createTypeErrorFieldsNotAnObject();
            }
            JSDynamicObject options = getOptionsObject(optionsParam, this, errorBranch, optionUndefined);
            JSTemporalDateTimeRecord result = TemporalUtil.isoDateFromFields((JSDynamicObject) fields, options, getContext(),
                            isObjectNode, getOptionNode, toIntOrInfinityNode, identicalNode);

            return JSTemporalPlainDate.create(getContext(), getRealm(), result.getYear(), result.getMonth(), result.getDay(), calendar, this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object fields, Object optionsParam) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.5
    public abstract static class JSTemporalCalendarYearMonthFromFields extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarYearMonthFromFields(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object yearMonthFromFields(JSTemporalCalendarObject calendar, Object fields, Object optionsParam,
                        @Cached("createSameValue()") JSIdenticalNode identicalNode,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached JSToIntegerOrInfinityNode toIntOrInfinityNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            assert calendar.getId().equals(ISO8601);
            if (!isObject(fields)) {
                errorBranch.enter(this);
                throw TemporalErrors.createTypeErrorFieldsNotAnObject();
            }
            JSDynamicObject options = getOptionsObject(optionsParam, this, errorBranch, optionUndefined);
            JSTemporalYearMonthDayRecord result = TemporalUtil.isoYearMonthFromFields((JSDynamicObject) fields, options, getContext(),
                            isObjectNode, getOptionNode, toIntOrInfinityNode, identicalNode);
            return JSTemporalPlainYearMonth.create(getContext(), getRealm(),
                            result.getYear(), result.getMonth(), calendar, result.getDay(), this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object fields, Object optionsParam) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.6
    public abstract static class JSTemporalCalendarMonthDayFromFields extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarMonthDayFromFields(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object monthDayFromFields(JSTemporalCalendarObject calendar, Object fields, Object optionsParam,
                        @Cached("createSameValue()") JSIdenticalNode identicalNode,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached JSToIntegerOrInfinityNode toIntOrInfinityNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            assert calendar.getId().equals(ISO8601);
            if (!isObject(fields)) {
                errorBranch.enter(this);
                throw TemporalErrors.createTypeErrorFieldsNotAnObject();
            }
            JSDynamicObject options = getOptionsObject(optionsParam, this, errorBranch, optionUndefined);
            JSTemporalYearMonthDayRecord result = TemporalUtil.isoMonthDayFromFields((JSDynamicObject) fields, options, getContext(),
                            isObjectNode, getOptionNode, toIntOrInfinityNode, identicalNode);
            return JSTemporalPlainMonthDay.create(getContext(), getRealm(),
                            result.getMonth(), result.getDay(), calendar, result.getYear(), this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object fields, Object optionsParam) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.7
    public abstract static class JSTemporalCalendarDateAdd extends JSTemporalBuiltinOperation {

        protected final ConditionProfile needConstrain = ConditionProfile.create();

        protected JSTemporalCalendarDateAdd(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object dateAdd(JSTemporalCalendarObject calendar, Object dateObj, Object durationObj, Object optionsParam,
                        @Cached("create(getContext())") ToTemporalDurationNode toTemporalDurationNode,
                        @Cached("createKeys(getContext())") EnumerableOwnPropertyNamesNode namesNode,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            assert calendar.getId().equals(ISO8601);
            JSTemporalPlainDateObject date = toTemporalDate.execute(dateObj, Undefined.instance);
            JSTemporalDurationObject duration = toTemporalDurationNode.execute(durationObj);
            JSDynamicObject options = getOptionsObject(optionsParam, this, errorBranch, optionUndefined);
            Overflow overflow = TemporalUtil.toTemporalOverflow(options, getOptionNode);
            JSRealm realm = getRealm();
            JSTemporalDurationRecord balanceResult = TemporalUtil.balanceDuration(getContext(), realm, namesNode, duration.getDays(), duration.getHours(), duration.getMinutes(), duration.getSeconds(),
                            duration.getMilliseconds(), duration.getMicroseconds(), duration.getNanoseconds(), Unit.DAY);
            JSTemporalDateTimeRecord result = TemporalUtil.addISODate(date.getYear(), date.getMonth(), date.getDay(),
                            dtoiConstrain(duration.getYears()), dtoiConstrain(duration.getMonths()), dtoiConstrain(duration.getWeeks()), dtoiConstrain(balanceResult.getDays()), overflow);
            return JSTemporalPlainDate.create(getContext(), realm, result.getYear(), result.getMonth(), result.getDay(), calendar, this, errorBranch);
        }

        // in contrast to `dtoi`, set to Integer.MAX_VALUE/MIN_VALUE if outside range.
        // later operations either CONSTRAIN or REJECT anyway!
        protected int dtoiConstrain(double d) {
            if (needConstrain.profile(JSRuntime.doubleIsRepresentableAsInt(d))) {
                return (int) d;
            } else {
                return d > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object dateObj, Object durationObj, Object optionsParam) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.8
    public abstract static class JSTemporalCalendarDateUntil extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarDateUntil(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object dateUntil(JSTemporalCalendarObject calendar, Object oneObj, Object twoObj, Object optionsParam,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached TemporalGetOptionNode getOptionNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedConditionProfile optionUndefined) {
            assert calendar.getId().equals(ISO8601);
            JSTemporalPlainDateObject one = toTemporalDate.execute(oneObj, Undefined.instance);
            JSTemporalPlainDateObject two = toTemporalDate.execute(twoObj, Undefined.instance);
            JSDynamicObject options = getOptionsObject(optionsParam, this, errorBranch, optionUndefined);
            Unit largestUnit = toLargestTemporalUnit(options, TemporalUtil.listTime, AUTO, Unit.DAY, equalNode, getOptionNode, this, errorBranch);
            JSTemporalDurationRecord result = JSTemporalPlainDate.differenceISODate(
                            one.getYear(), one.getMonth(), one.getDay(), two.getYear(), two.getMonth(), two.getDay(),
                            largestUnit);
            return JSTemporalDuration.createTemporalDuration(getContext(), getRealm(),
                            result.getYears(), result.getMonths(), result.getWeeks(), result.getDays(),
                            0, 0, 0, 0, 0, 0, this, errorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object oneObj, Object twoObj, Object optionsParam) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.9
    public abstract static class JSTemporalCalendarYear extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarYear(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long year(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            if (JSTemporalPlainDate.isJSTemporalPlainDate(temporalDateLike)) {
                return ((JSTemporalPlainDateObject) temporalDateLike).getYear();
            } else if (JSTemporalPlainYearMonth.isJSTemporalPlainYearMonth(temporalDateLike)) {
                return ((JSTemporalPlainYearMonthObject) temporalDateLike).getYear();
            } else {
                JSTemporalPlainDateObject td = toTemporalDate.execute(temporalDateLike, Undefined.instance);
                return td.getYear();
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.10
    public abstract static class JSTemporalCalendarMonth extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarMonth(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long month(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            Object dateLike = temporalDateLike;

            if (JSTemporalPlainMonthDay.isJSTemporalPlainMonthDay(dateLike)) {
                throw Errors.createTypeError("PlainMonthDay not expected");
            }

            if (!isObject(dateLike) ||
                            (!JSTemporalPlainDate.isJSTemporalPlainDate(dateLike) && !JSTemporalPlainDateTime.isJSTemporalPlainDateTime(dateLike) &&
                                            !JSTemporalPlainYearMonth.isJSTemporalPlainYearMonth(dateLike))) {
                dateLike = toTemporalDate.execute(dateLike, Undefined.instance);
            }
            assert dateLike instanceof TemporalMonth;
            return ((TemporalMonth) dateLike).getMonth();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.11
    public abstract static class JSTemporalCalendarMonthCode extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarMonthCode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected TruffleString monthCode(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            Object dateLike = temporalDateLike;
            if (!isObject(dateLike) || (!JSTemporalPlainDate.isJSTemporalPlainDate(dateLike) && !JSTemporalPlainDateTime.isJSTemporalPlainDateTime(temporalDateLike) &&
                            !(dateLike instanceof JSTemporalPlainMonthDayObject) && !JSTemporalPlainYearMonth.isJSTemporalPlainYearMonth(dateLike))) {
                dateLike = toTemporalDate.execute(dateLike, Undefined.instance);
            }
            return TemporalUtil.isoMonthCode((TemporalMonth) dateLike);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.12
    public abstract static class JSTemporalCalendarDay extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarDay(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long day(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            JSDynamicObject tdl = Undefined.instance;
            if (!JSTemporalPlainDate.isJSTemporalPlainDate(temporalDateLike) && !JSTemporalPlainDateTime.isJSTemporalPlainDateTime(temporalDateLike) &&
                            !JSTemporalPlainMonthDay.isJSTemporalPlainMonthDay(temporalDateLike)) {
                tdl = toTemporalDate.execute(temporalDateLike, Undefined.instance);
            } else {
                tdl = (JSDynamicObject) temporalDateLike;
            }
            return TemporalUtil.isoDay(tdl);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.13
    public abstract static class JSTemporalCalendarDayOfWeek extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarDayOfWeek(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long dayOfWeek(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            JSTemporalPlainDateObject date = toTemporalDate.execute(temporalDateLike, Undefined.instance);
            return TemporalUtil.toISODayOfWeek(date.getYear(), date.getMonth(), date.getDay());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.14
    public abstract static class JSTemporalCalendarDayOfYear extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarDayOfYear(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long dayOfYear(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            JSTemporalPlainDateObject date = toTemporalDate.execute(temporalDateLike, Undefined.instance);
            return TemporalUtil.toISODayOfYear(date.getYear(), date.getMonth(), date.getDay());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.15
    public abstract static class JSTemporalCalendarWeekOfYear extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarWeekOfYear(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long weekOfYear(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            JSTemporalPlainDateObject date = toTemporalDate.execute(temporalDateLike, Undefined.instance);
            return TemporalUtil.toISOWeekOfYear(date.getYear(), date.getMonth(), date.getDay());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.16
    public abstract static class JSTemporalCalendarDaysInWeek extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarDaysInWeek(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long daysInWeek(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            toTemporalDate.execute(temporalDateLike, Undefined.instance);
            return 7;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.17
    public abstract static class JSTemporalCalendarDaysInMonth extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarDaysInMonth(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long daysInMonth(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            Object dateLike = temporalDateLike;
            if (!isObject(dateLike) || (!JSTemporalPlainDate.isJSTemporalPlainDate(dateLike) && !JSTemporalPlainYearMonth.isJSTemporalPlainYearMonth(temporalDateLike))) {
                dateLike = toTemporalDate.execute(dateLike, Undefined.instance);
            }
            return TemporalUtil.isoDaysInMonth(
                            ((TemporalYear) dateLike).getYear(),
                            ((TemporalMonth) dateLike).getMonth());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.18
    public abstract static class JSTemporalCalendarDaysInYear extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarDaysInYear(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected int daysInYear(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            int year = 0;
            if (JSTemporalPlainDate.isJSTemporalPlainDate(temporalDateLike)) {
                year = ((JSTemporalPlainDateObject) temporalDateLike).getYear();
            } else if (JSTemporalPlainYearMonth.isJSTemporalPlainYearMonth(temporalDateLike)) {
                year = ((JSTemporalPlainYearMonthObject) temporalDateLike).getYear();
            } else {
                JSTemporalPlainDateObject dateLike = toTemporalDate.execute(temporalDateLike, Undefined.instance);
                year = dateLike.getYear();
            }
            return TemporalUtil.isoDaysInYear(year);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.19
    public abstract static class JSTemporalCalendarMonthsInYear extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarMonthsInYear(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long monthsInYear(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            if (!JSTemporalPlainDate.isJSTemporalPlainDate(temporalDateLike) && !JSTemporalPlainYearMonth.isJSTemporalPlainYearMonth(temporalDateLike)) {
                toTemporalDate.execute(temporalDateLike, Undefined.instance); // discard result
            }
            return 12;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }

    // 12.4.20
    public abstract static class JSTemporalCalendarInLeapYear extends JSTemporalBuiltinOperation {

        protected JSTemporalCalendarInLeapYear(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected boolean inLeapYear(JSTemporalCalendarObject calendar, Object temporalDateLike,
                        @Cached("create(getContext())") ToTemporalDateNode toTemporalDate) {
            assert calendar.getId().equals(ISO8601);
            int year = 0;
            if (JSTemporalPlainDate.isJSTemporalPlainDate(temporalDateLike)) {
                year = ((JSTemporalPlainDateObject) temporalDateLike).getYear();
            } else if (JSTemporalPlainYearMonth.isJSTemporalPlainYearMonth(temporalDateLike)) {
                year = ((JSTemporalPlainYearMonthObject) temporalDateLike).getYear();
            } else {
                JSTemporalPlainDateObject dateLike = toTemporalDate.execute(temporalDateLike, Undefined.instance);
                year = dateLike.getYear();
            }
            return TemporalUtil.isISOLeapYear(year);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJSTemporalCalendar(thisObj)")
        protected static Object invalidReceiver(Object thisObj, Object temporalDateLike) {
            throw TemporalErrors.createTypeErrorTemporalCalendarExpected();
        }
    }
}
