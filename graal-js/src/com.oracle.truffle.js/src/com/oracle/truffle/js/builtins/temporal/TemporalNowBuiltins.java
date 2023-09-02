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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.js.builtins.JSBuiltinsContainer;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowInstantNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowPlainDateISONodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowPlainDateNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowPlainDateTimeISONodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowPlainDateTimeNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowPlainTimeISONodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowTimeZoneNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowZonedDateTimeISONodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalNowBuiltinsFactory.TemporalNowZonedDateTimeNodeGen;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalCalendarNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalTimeZoneNode;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalCalendarObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalInstantObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDate;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainDateTimeObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainTime;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainTimeObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalTimeZoneObject;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTimeObject;
import com.oracle.truffle.js.runtime.util.TemporalConstants;
import com.oracle.truffle.js.runtime.util.TemporalUtil;

/**
 * Contains builtins for Temporal.now.
 */
public class TemporalNowBuiltins extends JSBuiltinsContainer.SwitchEnum<TemporalNowBuiltins.TemporalNow> {

    public static final JSBuiltinsContainer BUILTINS = new TemporalNowBuiltins();

    protected TemporalNowBuiltins() {
        super(TemporalConstants.NOW, TemporalNow.class);
    }

    public enum TemporalNow implements BuiltinEnum<TemporalNow> {
        timeZone(0),
        instant(0),
        plainDateTime(1),
        plainDateTimeISO(0),
        zonedDateTime(1),
        zonedDateTimeISO(0),
        plainDate(1),
        plainDateISO(0),
        plainTimeISO(0);

        private final int length;

        TemporalNow(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, TemporalNow builtinEnum) {
        switch (builtinEnum) {
            case timeZone:
                return TemporalNowTimeZoneNodeGen.create(context, builtin, args().fixedArgs(0).createArgumentNodes(context));
            case instant:
                return TemporalNowInstantNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
            case plainDateTime:
                return TemporalNowPlainDateTimeNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
            case plainDateTimeISO:
                return TemporalNowPlainDateTimeISONodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            case zonedDateTime:
                return TemporalNowZonedDateTimeNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
            case zonedDateTimeISO:
                return TemporalNowZonedDateTimeISONodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            case plainDate:
                return TemporalNowPlainDateNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
            case plainDateISO:
                return TemporalNowPlainDateISONodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            case plainTimeISO:
                return TemporalNowPlainTimeISONodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            default:
                return null;
        }
    }

    public abstract static class TemporalNowTimeZoneNode extends JSBuiltinNode {

        protected TemporalNowTimeZoneNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalTimeZoneObject timeZone() {
            return TemporalUtil.systemTimeZone(getContext(), getRealm());
        }
    }

    public abstract static class TemporalNowInstantNode extends JSBuiltinNode {

        protected TemporalNowInstantNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalInstantObject instant() {
            return TemporalUtil.systemInstant(getContext(), getRealm());
        }
    }

    public abstract static class TemporalNowPlainDateTimeNode extends JSBuiltinNode {

        protected TemporalNowPlainDateTimeNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalPlainDateTimeObject plainDateTime(Object calendar, Object temporalTimeZoneLike,
                        @Cached("create(getContext())") ToTemporalCalendarNode toTemporalCalendar,
                        @Cached("create(getContext())") ToTemporalTimeZoneNode toTemporalTimeZone) {
            return TemporalUtil.systemDateTime(temporalTimeZoneLike, calendar, getContext(), getRealm(), toTemporalCalendar, toTemporalTimeZone);
        }
    }

    public abstract static class TemporalNowPlainDateTimeISONode extends JSBuiltinNode {

        protected TemporalNowPlainDateTimeISONode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalPlainDateTimeObject plainDateTimeISO(Object temporalTimeZoneLike,
                        @Cached("create(getContext())") ToTemporalCalendarNode toTemporalCalendar,
                        @Cached("create(getContext())") ToTemporalTimeZoneNode toTemporalTimeZone) {
            JSRealm realm = getRealm();
            JSTemporalCalendarObject iso8601Calendar = TemporalUtil.getISO8601Calendar(getContext(), realm);
            return TemporalUtil.systemDateTime(temporalTimeZoneLike, iso8601Calendar, getContext(), realm, toTemporalCalendar, toTemporalTimeZone);
        }
    }

    public abstract static class TemporalNowZonedDateTimeNode extends JSBuiltinNode {

        protected TemporalNowZonedDateTimeNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalZonedDateTimeObject zonedDateTime(Object calendar, Object temporalTimeZoneLike,
                        @Cached("create(getContext())") ToTemporalCalendarNode toTemporalCalendar,
                        @Cached("create(getContext())") ToTemporalTimeZoneNode toTemporalTimeZone) {
            return TemporalUtil.systemZonedDateTime(temporalTimeZoneLike, calendar, getContext(), getRealm(), toTemporalCalendar, toTemporalTimeZone);
        }
    }

    public abstract static class TemporalNowZonedDateTimeISONode extends JSBuiltinNode {

        protected TemporalNowZonedDateTimeISONode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalZonedDateTimeObject zonedDateTimeISO(Object temporalTimeZoneLike,
                        @Cached("create(getContext())") ToTemporalCalendarNode toTemporalCalendar,
                        @Cached("create(getContext())") ToTemporalTimeZoneNode toTemporalTimeZone) {
            JSRealm realm = getRealm();
            JSTemporalCalendarObject iso8601Calendar = TemporalUtil.getISO8601Calendar(getContext(), realm);
            return TemporalUtil.systemZonedDateTime(temporalTimeZoneLike, iso8601Calendar, getContext(), realm, toTemporalCalendar, toTemporalTimeZone);
        }
    }

    public abstract static class TemporalNowPlainDateNode extends JSBuiltinNode {

        protected TemporalNowPlainDateNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalPlainDateObject plainDate(Object calendar, Object temporalTimeZoneLike,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached("create(getContext())") ToTemporalCalendarNode toTemporalCalendar,
                        @Cached("create(getContext())") ToTemporalTimeZoneNode toTemporalTimeZone) {
            JSTemporalPlainDateTimeObject dateTime = TemporalUtil.systemDateTime(temporalTimeZoneLike, calendar, getContext(), getRealm(), toTemporalCalendar, toTemporalTimeZone);
            return JSTemporalPlainDate.create(getContext(), getRealm(),
                            dateTime.getYear(), dateTime.getMonth(), dateTime.getDay(), dateTime.getCalendar(), this, errorBranch);
        }
    }

    public abstract static class TemporalNowPlainDateISONode extends JSBuiltinNode {

        protected TemporalNowPlainDateISONode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalPlainDateObject plainDateISO(Object temporalTimeZoneLike,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached("create(getContext())") ToTemporalCalendarNode toTemporalCalendar,
                        @Cached("create(getContext())") ToTemporalTimeZoneNode toTemporalTimeZone) {
            JSRealm realm = getRealm();
            JSTemporalCalendarObject calendar = TemporalUtil.getISO8601Calendar(getContext(), realm);
            JSTemporalPlainDateTimeObject dateTime = TemporalUtil.systemDateTime(temporalTimeZoneLike, calendar, getContext(), realm, toTemporalCalendar, toTemporalTimeZone);
            return JSTemporalPlainDate.create(getContext(), getRealm(),
                            dateTime.getYear(), dateTime.getMonth(), dateTime.getDay(), dateTime.getCalendar(), this, errorBranch);
        }
    }

    public abstract static class TemporalNowPlainTimeISONode extends JSBuiltinNode {

        protected TemporalNowPlainTimeISONode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSTemporalPlainTimeObject plainTimeISO(Object temporalTimeZoneLike,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached("create(getContext())") ToTemporalCalendarNode toTemporalCalendar,
                        @Cached("create(getContext())") ToTemporalTimeZoneNode toTemporalTimeZone) {
            JSRealm realm = getRealm();
            JSTemporalCalendarObject calendar = TemporalUtil.getISO8601Calendar(getContext(), realm);
            JSTemporalPlainDateTimeObject dateTime = TemporalUtil.systemDateTime(temporalTimeZoneLike, calendar, getContext(), realm, toTemporalCalendar, toTemporalTimeZone);
            return JSTemporalPlainTime.create(getContext(), getRealm(),
                            dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond(), dateTime.getMillisecond(), dateTime.getMicrosecond(), dateTime.getNanosecond(), this, errorBranch);
        }
    }
}
