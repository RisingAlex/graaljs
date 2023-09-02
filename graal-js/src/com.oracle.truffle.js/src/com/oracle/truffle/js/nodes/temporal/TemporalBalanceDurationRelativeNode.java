/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.temporal;

import static com.oracle.truffle.js.runtime.util.TemporalConstants.MONTH;
import static com.oracle.truffle.js.runtime.util.TemporalUtil.dtol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.GetMethodNode;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.builtins.JSOrdinary;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDuration;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDurationObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDurationRecord;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalRelativeDateRecord;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalConstants;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;

/**
 * Implementation of the Temporal balanceDurationRelative operation.
 */
public abstract class TemporalBalanceDurationRelativeNode extends JavaScriptBaseNode {

    protected final JSContext ctx;
    @Child private JSFunctionCallNode callDateAddNode;
    @Child private GetMethodNode getMethodDateAddNode;
    @Child private JSFunctionCallNode callDateUntilNode;
    @Child private GetMethodNode getMethodDateUntilNode;

    protected TemporalBalanceDurationRelativeNode(JSContext ctx) {
        this.ctx = ctx;
    }

    public abstract JSTemporalDurationRecord execute(double year, double month, double week, double day, TemporalUtil.Unit largestUnit, JSDynamicObject relativeToParam);

    // TODO still using (some) long arithmetics here, should use double?
    @Specialization
    protected JSTemporalDurationRecord balanceDurationRelative(double y, double m, double w, double d, TemporalUtil.Unit largestUnit, JSDynamicObject relTo,
                    @Cached InlinedBranchProfile errorBranch,
                    @Cached InlinedConditionProfile unitIsYear,
                    @Cached InlinedConditionProfile unitIsMonth,
                    @Cached InlinedConditionProfile unitIsDay,
                    @Cached("create(ctx)") ToTemporalDateNode toTemporalDateNode,
                    @Cached("create(ctx)") TemporalMoveRelativeDateNode moveRelativeDateNode) {
        long years = dtol(y);
        long months = dtol(m);
        long weeks = dtol(w);
        long days = dtol(d);

        if (unitIsDay.profile(this, (largestUnit != TemporalUtil.Unit.YEAR && largestUnit != TemporalUtil.Unit.MONTH && largestUnit != TemporalUtil.Unit.WEEK) ||
                        (years == 0 && months == 0 && weeks == 0 && days == 0))) {
            return JSTemporalDurationRecord.createWeeks(years, months, weeks, days, 0, 0, 0, 0, 0, 0);
        }
        if (relTo == Undefined.instance) {
            errorBranch.enter(this);
            throw TemporalErrors.createRangeErrorRelativeToNotUndefined();
        }
        long sign = TemporalUtil.durationSign(years, months, weeks, days, 0, 0, 0, 0, 0, 0);
        assert sign != 0;
        JSRealm realm = getRealm();
        JSDynamicObject oneYear = JSTemporalDuration.createTemporalDuration(ctx, realm, sign, 0, 0, 0, 0, 0, 0, 0, 0, 0, this, errorBranch);
        JSDynamicObject oneMonth = JSTemporalDuration.createTemporalDuration(ctx, realm, 0, sign, 0, 0, 0, 0, 0, 0, 0, 0, this, errorBranch);
        JSDynamicObject oneWeek = JSTemporalDuration.createTemporalDuration(ctx, realm, 0, 0, sign, 0, 0, 0, 0, 0, 0, 0, this, errorBranch);
        JSTemporalPlainDateObject relativeTo = toTemporalDateNode.execute(relTo, Undefined.instance);
        JSDynamicObject calendar = relativeTo.getCalendar();
        if (unitIsYear.profile(this, largestUnit == TemporalUtil.Unit.YEAR)) {
            return getUnitYear(years, months, weeks, days, sign, oneYear, oneMonth, relativeTo, calendar,
                            this, errorBranch, moveRelativeDateNode);
        } else if (unitIsMonth.profile(this, largestUnit == TemporalUtil.Unit.MONTH)) {
            return getUnitMonth(years, months, weeks, days, sign, oneMonth, relativeTo, calendar,
                            moveRelativeDateNode);
        } else {
            return getUnitWeek(largestUnit, years, months, weeks, days, sign, oneWeek, relativeTo, calendar,
                            moveRelativeDateNode);
        }
    }

    private JSTemporalDurationRecord getUnitYear(long yearsP, long monthsP, long weeks, long daysP, long sign, JSDynamicObject oneYear, JSDynamicObject oneMonth, JSDynamicObject relativeToP,
                    JSDynamicObject calendar, Node node, InlinedBranchProfile errorBranch, TemporalMoveRelativeDateNode moveRelativeDateNode) {
        long years = yearsP;
        long months = monthsP;
        long days = daysP;
        JSDynamicObject relativeTo = relativeToP;
        JSTemporalRelativeDateRecord moveResult = moveRelativeDateNode.execute(calendar, relativeTo, oneYear);
        relativeTo = moveResult.getRelativeTo();
        long oneYearDays = moveResult.getDays();
        while (Math.abs(days) >= Math.abs(oneYearDays)) {
            days = days - oneYearDays;
            years = years + sign;
            moveResult = moveRelativeDateNode.execute(calendar, relativeTo, oneYear);
            relativeTo = moveResult.getRelativeTo();
            oneYearDays = moveResult.getDays();
        }
        moveResult = moveRelativeDateNode.execute(calendar, relativeTo, oneMonth);
        relativeTo = moveResult.getRelativeTo();
        long oneMonthDays = moveResult.getDays();
        while (Math.abs(days) >= Math.abs(oneMonthDays)) {
            days = days - oneMonthDays;
            months = months + sign;
            moveResult = moveRelativeDateNode.execute(calendar, relativeTo, oneMonth);
            relativeTo = moveResult.getRelativeTo();
            oneMonthDays = moveResult.getDays();
        }

        Object dateAdd = getDateAdd(calendar);
        JSDynamicObject newRelativeTo = calendarDateAdd(calendar, relativeTo, oneYear, Undefined.instance, dateAdd, node, errorBranch);

        Object dateUntil = getDateUntil(calendar);
        JSObject untilOptions = JSOrdinary.createWithNullPrototype(ctx);
        JSObjectUtil.putDataProperty(untilOptions, TemporalConstants.LARGEST_UNIT, MONTH);
        JSTemporalDurationObject untilResult = calendarDateUntil(calendar, relativeTo, newRelativeTo, untilOptions, dateUntil);

        long oneYearMonths = dtol(untilResult.getMonths());
        while (Math.abs(months) >= Math.abs((oneYearMonths))) {
            months = months - oneYearMonths;
            years = years + sign;
            relativeTo = newRelativeTo;

            newRelativeTo = calendarDateAdd(calendar, relativeTo, oneYear, Undefined.instance, dateAdd, node, errorBranch);
            untilOptions = JSOrdinary.createWithNullPrototype(ctx);
            JSObjectUtil.putDataProperty(untilOptions, TemporalConstants.LARGEST_UNIT, MONTH);
            untilResult = calendarDateUntil(calendar, relativeTo, newRelativeTo, untilOptions, dateUntil);
            oneYearMonths = dtol(untilResult.getMonths());
        }
        return JSTemporalDurationRecord.createWeeks(years, months, weeks, days, 0, 0, 0, 0, 0, 0);
    }

    private static JSTemporalDurationRecord getUnitMonth(long years, long monthsP, long weeks, long daysP, long sign, JSDynamicObject oneMonth, JSDynamicObject relativeToP, JSDynamicObject calendar,
                    TemporalMoveRelativeDateNode moveRelativeDateNode) {
        long months = monthsP;
        long days = daysP;
        JSDynamicObject relativeTo = relativeToP;
        JSTemporalRelativeDateRecord moveResult = moveRelativeDateNode.execute(calendar, relativeTo, oneMonth);
        relativeTo = moveResult.getRelativeTo();
        long oneMonthDays = moveResult.getDays();
        while (Math.abs(days) >= Math.abs(oneMonthDays)) {
            days = days - oneMonthDays;
            months = months + sign;
            moveResult = moveRelativeDateNode.execute(calendar, relativeTo, oneMonth);
            relativeTo = moveResult.getRelativeTo();
            oneMonthDays = moveResult.getDays();
        }
        return JSTemporalDurationRecord.createWeeks(years, months, weeks, days, 0, 0, 0, 0, 0, 0);
    }

    private static JSTemporalDurationRecord getUnitWeek(TemporalUtil.Unit largestUnit, long years, long months, long weeksP, long daysP, long sign, JSDynamicObject oneWeek,
                    JSDynamicObject relativeToP, JSDynamicObject calendar, TemporalMoveRelativeDateNode moveRelativeDateNode) {
        long weeks = weeksP;
        long days = daysP;
        JSDynamicObject relativeTo = relativeToP;
        assert largestUnit == TemporalUtil.Unit.WEEK;
        JSTemporalRelativeDateRecord moveResult = moveRelativeDateNode.execute(calendar, relativeTo, oneWeek);
        relativeTo = moveResult.getRelativeTo();
        long oneWeekDays = moveResult.getDays();
        while (Math.abs(days) >= Math.abs(oneWeekDays)) {
            days = days - oneWeekDays;
            weeks = weeks + sign;
            moveResult = moveRelativeDateNode.execute(calendar, relativeTo, oneWeek);
            relativeTo = moveResult.getRelativeTo();
            oneWeekDays = moveResult.getDays();
        }
        return JSTemporalDurationRecord.createWeeks(years, months, weeks, days, 0, 0, 0, 0, 0, 0);
    }

    protected JSTemporalPlainDateObject calendarDateAdd(JSDynamicObject calendar, JSDynamicObject date, JSDynamicObject duration, JSDynamicObject options, Object dateAdd,
                    Node node, InlinedBranchProfile errorBranch) {
        if (callDateAddNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callDateAddNode = insert(JSFunctionCallNode.createCall());
        }
        Object addedDate = callDateAddNode.executeCall(JSArguments.create(calendar, dateAdd, date, duration, options));
        return TemporalUtil.requireTemporalDate(addedDate, node, errorBranch);
    }

    protected JSTemporalDurationObject calendarDateUntil(JSDynamicObject calendar, JSDynamicObject date, JSDynamicObject duration, JSDynamicObject options, Object dateUntil) {
        if (callDateUntilNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callDateUntilNode = insert(JSFunctionCallNode.createCall());
        }
        Object addedDate = callDateUntilNode.executeCall(JSArguments.create(calendar, dateUntil, date, duration, options));
        return TemporalUtil.requireTemporalDuration(addedDate);
    }

    private Object getDateAdd(JSDynamicObject obj) {
        if (getMethodDateAddNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMethodDateAddNode = insert(GetMethodNode.create(ctx, TemporalConstants.DATE_ADD));
        }
        return getMethodDateAddNode.executeWithTarget(obj);
    }

    private Object getDateUntil(JSDynamicObject obj) {
        if (getMethodDateUntilNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMethodDateUntilNode = insert(GetMethodNode.create(ctx, TemporalConstants.DATE_UNTIL));
        }
        return getMethodDateUntilNode.executeWithTarget(obj);
    }

}
