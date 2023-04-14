/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.nodes.interop;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOfFactory;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Handles conversions of (potentially) foreign objects to Espresso types.
 */
@NodeInfo(shortName = "Convert to Espresso")
public abstract class ToEspressoNode extends EspressoNode {

    public abstract StaticObject execute(Object value) throws UnsupportedTypeException;

    @TruffleBoundary
    public static ToEspressoNode create(Klass targetType, Meta meta) {
        if (targetType.isPrimitive()) {
            switch (targetType.getJavaKind()) {
                case Boolean:
                    return ToEspressoNodeFactory.ToBooleanNodeGen.create(ToPrimitiveFactory.ToBooleanNodeGen.create());
                case Byte:
                    return ToEspressoNodeFactory.ToByteNodeGen.create(ToPrimitiveFactory.ToByteNodeGen.create());
                case Short:
                    return ToEspressoNodeFactory.ToShortNodeGen.create(ToPrimitiveFactory.ToShortNodeGen.create());
                case Int:
                    return ToEspressoNodeFactory.ToIntegerNodeGen.create(ToPrimitiveFactory.ToIntNodeGen.create());
                case Float:
                    return ToEspressoNodeFactory.ToFloatNodeGen.create(ToPrimitiveFactory.ToFloatNodeGen.create());
                case Long:
                    return ToEspressoNodeFactory.ToLongNodeGen.create(ToPrimitiveFactory.ToLongNodeGen.create());
                case Double:
                    return ToEspressoNodeFactory.ToDoubleNodeGen.create(ToPrimitiveFactory.ToDoubleNodeGen.create());
                case Char:
                    return ToEspressoNodeFactory.ToCharNodeGen.create(ToPrimitiveFactory.ToCharNodeGen.create());
                case Void:
                    return new ToVoid();
            }
        }
        if (targetType.getMeta().isBoxed(targetType)) {
            if (targetType == meta.java_lang_Boolean) {
                return ToEspressoNodeFactory.ToBooleanNodeGen.create(ToPrimitiveFactory.ToBooleanNodeGen.create());
            }
            if (targetType == meta.java_lang_Character) {
                return ToEspressoNodeFactory.ToCharNodeGen.create(ToPrimitiveFactory.ToCharNodeGen.create());
            }
            if (targetType == meta.java_lang_Integer) {
                return ToEspressoNodeFactory.ToIntegerNodeGen.create(ToPrimitiveFactory.ToIntNodeGen.create());
            }
            if (targetType == meta.java_lang_Byte) {
                return ToEspressoNodeFactory.ToByteNodeGen.create(ToPrimitiveFactory.ToByteNodeGen.create());
            }
            if (targetType == meta.java_lang_Short) {
                return ToEspressoNodeFactory.ToShortNodeGen.create(ToPrimitiveFactory.ToShortNodeGen.create());
            }
            if (targetType == meta.java_lang_Long) {
                return ToEspressoNodeFactory.ToLongNodeGen.create(ToPrimitiveFactory.ToLongNodeGen.create());
            }
            if (targetType == meta.java_lang_Float) {
                return ToEspressoNodeFactory.ToFloatNodeGen.create(ToPrimitiveFactory.ToFloatNodeGen.create());
            }
            if (targetType == meta.java_lang_Double) {
                return ToEspressoNodeFactory.ToDoubleNodeGen.create(ToPrimitiveFactory.ToDoubleNodeGen.create());
            }
        }
        if (targetType == meta.java_lang_Number) {
            return ToEspressoNodeFactory.ToNumberNodeGen.create();
        }
        if (targetType == meta._byte_array) {
            return ToEspressoNodeFactory.ToByteArrayNodeGen.create();
        }
        if (targetType.isArray()) {
            return ToEspressoNodeFactory.ToArrayNodeGen.create((ArrayKlass) targetType);
        }
        if (targetType.isJavaLangObject()) {
            return ToEspressoNodeFactory.ToJavaLangObjectNodeGen.create();
        }
        if (targetType == meta.java_lang_String) {
            return ToEspressoNodeFactory.ToStringNodeGen.create();
        }
        if (targetType.isInterface()) {
            if (isTypeMappingEnabled(targetType)) {
                return ToEspressoNodeFactory.ToMappedInterfaceNodeGen.create((ObjectKlass) targetType);
            } else if (targetType == meta.java_lang_CharSequence) {
                return ToEspressoNodeFactory.ToCharSequenceNodeGen.create();
            } else {
                return ToEspressoNodeFactory.ToUnknownNodeGen.create((ObjectKlass) targetType);
            }
        }
        if (isForeignException(targetType, meta)) {
            return ToEspressoNodeFactory.ToForeignExceptionNodeGen.create();
        }
        if (targetType == meta.java_lang_Throwable) {
            return ToEspressoNodeFactory.ToThrowableNodeGen.create();
        }
        if (targetType == meta.java_lang_Exception) {
            return ToEspressoNodeFactory.ToExceptionNodeGen.create();
        }
        if (targetType == meta.java_lang_RuntimeException) {
            return ToEspressoNodeFactory.ToRuntimeExceptionNodeGen.create();
        }
        if (targetType == meta.java_time_LocalDate) {
            return ToEspressoNodeFactory.ToLocalDateNodeGen.create();
        }
        if (targetType == meta.java_time_LocalTime) {
            return ToEspressoNodeFactory.ToLocalTimeNodeGen.create();
        }
        if (targetType == meta.java_time_LocalDateTime) {
            return ToEspressoNodeFactory.ToLocalDateTimeNodeGen.create();
        }
        if (targetType == meta.java_time_ZonedDateTime) {
            return ToEspressoNodeFactory.ToZonedDateTimeNodeGen.create();
        }
        if (targetType == meta.java_time_Instant) {
            return ToEspressoNodeFactory.ToInstantNodeGen.create();
        }
        if (targetType == meta.java_time_Duration) {
            return ToEspressoNodeFactory.ToDurationNodeGen.create();
        }
        if (targetType == meta.java_time_ZoneId) {
            return ToEspressoNodeFactory.ToZoneIdNodeGen.create();
        }
        if (targetType == meta.java_util_Date) {
            return ToEspressoNodeFactory.ToDateNodeGen.create();
        }
        if (isTypeConverterEnabled(targetType)) {
            return ToEspressoNodeFactory.ToMappedTypeNodeGen.create((ObjectKlass) targetType);
        } else {
            return ToEspressoNodeFactory.ToUnknownNodeGen.create((ObjectKlass) targetType);
        }
    }

    @TruffleBoundary
    public static ToEspressoNode createUncached(Klass targetType, Meta meta) {
        if (targetType.isPrimitive()) {
            switch (targetType.getJavaKind()) {
                case Boolean:
                    return ToEspressoNodeFactory.ToBooleanNodeGen.create(ToPrimitiveFactory.ToBooleanNodeGen.getUncached());
                case Byte:
                    return ToEspressoNodeFactory.ToByteNodeGen.create(ToPrimitiveFactory.ToByteNodeGen.getUncached());
                case Short:
                    return ToEspressoNodeFactory.ToShortNodeGen.create(ToPrimitiveFactory.ToShortNodeGen.getUncached());
                case Int:
                    return ToEspressoNodeFactory.ToIntegerNodeGen.create(ToPrimitiveFactory.ToIntNodeGen.getUncached());
                case Float:
                    return ToEspressoNodeFactory.ToFloatNodeGen.create(ToPrimitiveFactory.ToFloatNodeGen.getUncached());
                case Long:
                    return ToEspressoNodeFactory.ToLongNodeGen.create(ToPrimitiveFactory.ToLongNodeGen.getUncached());
                case Double:
                    return ToEspressoNodeFactory.ToDoubleNodeGen.create(ToPrimitiveFactory.ToDoubleNodeGen.getUncached());
                case Char:
                    return ToEspressoNodeFactory.ToCharNodeGen.create(ToPrimitiveFactory.ToCharNodeGen.getUncached());
                case Void:
                    return new ToVoid();
            }
        }
        if (targetType.getMeta().isBoxed(targetType)) {
            if (targetType == meta.java_lang_Boolean) {
                return ToEspressoNodeFactory.ToBooleanNodeGen.create(ToPrimitiveFactory.ToBooleanNodeGen.getUncached());
            }
            if (targetType == meta.java_lang_Character) {
                return ToEspressoNodeFactory.ToCharNodeGen.create(ToPrimitiveFactory.ToCharNodeGen.getUncached());
            }
            if (targetType == meta.java_lang_Integer) {
                return ToEspressoNodeFactory.ToIntegerNodeGen.create(ToPrimitiveFactory.ToIntNodeGen.getUncached());
            }
            if (targetType == meta.java_lang_Byte) {
                return ToEspressoNodeFactory.ToByteNodeGen.create(ToPrimitiveFactory.ToByteNodeGen.getUncached());
            }
            if (targetType == meta.java_lang_Short) {
                return ToEspressoNodeFactory.ToShortNodeGen.create(ToPrimitiveFactory.ToShortNodeGen.getUncached());
            }
            if (targetType == meta.java_lang_Long) {
                return ToEspressoNodeFactory.ToLongNodeGen.create(ToPrimitiveFactory.ToLongNodeGen.getUncached());
            }
            if (targetType == meta.java_lang_Float) {
                return ToEspressoNodeFactory.ToFloatNodeGen.create(ToPrimitiveFactory.ToFloatNodeGen.getUncached());
            }
            if (targetType == meta.java_lang_Double) {
                return ToEspressoNodeFactory.ToDoubleNodeGen.create(ToPrimitiveFactory.ToDoubleNodeGen.getUncached());
            }
        }
        if (targetType == meta.java_lang_Number) {
            return ToEspressoNodeFactory.ToNumberNodeGen.getUncached();
        }
        if (targetType == meta._byte_array) {
            return ToEspressoNodeFactory.ToByteArrayNodeGen.getUncached();
        }
        if (targetType.isArray()) {
            throw new IllegalStateException("Generic arrays type mappings must be handled separately!");
        }
        if (targetType.isJavaLangObject()) {
            return ToEspressoNodeFactory.ToJavaLangObjectNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_String) {
            return ToEspressoNodeFactory.ToStringNodeGen.getUncached();
        }
        if (targetType.isInterface()) {
            if (targetType == meta.java_lang_CharSequence) {
                return ToEspressoNodeFactory.ToCharSequenceNodeGen.getUncached();
            }
            if (isTypeMappingEnabled(targetType)) {
                throw new IllegalStateException("Interface type mappings must be handled separately!");
            } else {
                throw new IllegalStateException("unknown types must be handled separately!");
            }
        }
        if (isForeignException(targetType, meta)) {
            return ToEspressoNodeFactory.ToForeignExceptionNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Throwable) {
            return ToEspressoNodeFactory.ToThrowableNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Exception) {
            return ToEspressoNodeFactory.ToExceptionNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_RuntimeException) {
            return ToEspressoNodeFactory.ToRuntimeExceptionNodeGen.getUncached();
        }
        if (targetType == meta.java_time_LocalDate) {
            return ToEspressoNodeFactory.ToLocalDateNodeGen.getUncached();
        }
        if (targetType == meta.java_time_LocalTime) {
            return ToEspressoNodeFactory.ToLocalTimeNodeGen.getUncached();
        }
        if (targetType == meta.java_time_LocalDateTime) {
            return ToEspressoNodeFactory.ToLocalDateTimeNodeGen.getUncached();
        }
        if (targetType == meta.java_time_ZonedDateTime) {
            return ToEspressoNodeFactory.ToZonedDateTimeNodeGen.getUncached();
        }
        if (targetType == meta.java_time_Instant) {
            return ToEspressoNodeFactory.ToInstantNodeGen.getUncached();
        }
        if (targetType == meta.java_time_Duration) {
            return ToEspressoNodeFactory.ToDurationNodeGen.getUncached();
        }
        if (targetType == meta.java_time_ZoneId) {
            return ToEspressoNodeFactory.ToZoneIdNodeGen.getUncached();
        }
        if (targetType == meta.java_util_Date) {
            return ToEspressoNodeFactory.ToDateNodeGen.getUncached();
        }
        throw new IllegalStateException("unknown types must be handled separately!");
    }

    private static boolean isForeignException(Klass klass, Meta meta) {
        return meta.polyglot != null /* polyglot enabled */ && meta.polyglot.ForeignException.equals(klass);
    }

    @GenerateUncached
    @NodeInfo(shortName = "Dynamic toEspresso node")
    @ReportPolymorphism
    public abstract static class Dynamic extends EspressoNode {
        protected static final int LIMIT = 8;

        public abstract StaticObject execute(Object value, Klass targetType) throws UnsupportedTypeException;

        protected static ToEspressoNode createToEspressoNode(Klass targetType) {
            return ToEspressoNode.create(targetType, targetType.getMeta());
        }

        protected static ToEspressoNode createUncachedToEspressoNode(Klass targetType) {
            return ToEspressoNode.createUncached(targetType, targetType.getMeta());
        }

        @Specialization(guards = "targetType == cachedTargetType", limit = "LIMIT")
        public StaticObject doCached(Object value, @SuppressWarnings("unused") Klass targetType,
                        @SuppressWarnings("unused") @Cached("targetType") Klass cachedTargetType,
                        @Cached("createToEspressoNode(cachedTargetType)") ToEspressoNode toEspressoNode) throws UnsupportedTypeException {
            return toEspressoNode.execute(value);
        }

        @Megamorphic
        @Specialization(replaces = "doCached")
        public StaticObject doGeneric(Object value, Klass targetType) throws UnsupportedTypeException {
            InteropLibrary interop = InteropLibrary.getUncached();
            if (interop.isNull(value)) {
                return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
            }
            if (value instanceof StaticObject) {
                StaticObject staticObject = (StaticObject) value;
                InstanceOf.Dynamic instanceOf = InstanceOfFactory.DynamicNodeGen.getUncached();
                if (StaticObject.isNull(staticObject) || instanceOf.execute(staticObject.getKlass(), targetType)) {
                    return (StaticObject) value; // pass through, NULL coercion not needed.
                }
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", targetType.getTypeAsString()));
            }
            if (targetType.isInterface()) {
                if (isTypeMappingEnabled(targetType)) {
                    try {
                        Object metaObject = getMetaObjectOrThrow(value, interop);
                        ObjectKlass proxyKlass = LookupProxyKlassNodeGen.getUncached().execute(metaObject, getMetaName(metaObject, interop), targetType);
                        if (proxyKlass != null) {
                            targetType.safeInitialize();
                            return StaticObject.createForeign(getLanguage(), proxyKlass, value, interop);
                        }
                        throw new ClassCastException();
                    } catch (ClassCastException e) {
                        throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
                    }
                }
            }
            if (targetType.isArray()) {
                if (targetType == getMeta()._byte_array) {
                    if (interop.hasBufferElements(value) && !isHostString(value)) {
                        return StaticObject.createForeign(EspressoLanguage.get(this), getMeta()._byte_array, value, interop);
                    }
                    throw UnsupportedTypeException.create(new Object[]{value}, getMeta()._byte_array.getTypeAsString());
                } else {
                    if (interop.hasArrayElements(value) && !isHostString(value)) {
                        return StaticObject.createForeign(EspressoLanguage.get(this), targetType, value, interop);
                    }
                    throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
                }
            }
            if (isTypeConverterEnabled(targetType)) {
                try {
                    Object metaObject = getMetaObjectOrThrow(value, interop);
                    String metaName = getMetaName(metaObject, interop);

                    // check if there's a specific type mapping available
                    PolyglotTypeMappings.TypeConverter converter = LookupTypeConverterNodeGen.getUncached().execute(metaName);
                    if (converter != null) {
                        return (StaticObject) converter.convert(StaticObject.createForeign(getLanguage(), targetType, value, interop));
                    }
                    throw new ClassCastException();
                } catch (ClassCastException e) {
                    throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
                }
            }
            try {
                return createUncachedToEspressoNode(targetType).execute(value);
            } catch (IllegalStateException ex) {
                // hit the unknown type case, so inline generic handling for that here
                if (targetType instanceof ObjectKlass) {
                    try {
                        checkHasAllFieldsOrThrow(value, (ObjectKlass) targetType, interop, getMeta());
                        return StaticObject.createForeign(getLanguage(), targetType, value, interop);
                    } catch (ClassCastException e) {
                        throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
                    }
                }
                throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
            }
        }
    }

    @NodeInfo(shortName = "void target type")
    @GenerateUncached
    static final class ToVoid extends ToEspressoNode {

        @Override
        public StaticObject execute(Object value) throws UnsupportedTypeException {
            return StaticObject.NULL;
        }
    }

    @NodeInfo(shortName = "integer target type")
    abstract static class ToInteger extends ToEspressoNode {
        protected static final int LIMIT = 2;

        private final ToPrimitive.ToInt toInt;

        ToInteger(ToPrimitive.ToInt toInt) {
            this.toInt = toInt;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doInteger(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxInteger((int) toInt.execute(value));
        }
    }

    @NodeInfo(shortName = "boolean target type")
    abstract static class ToBoolean extends ToEspressoNode {
        protected static final int LIMIT = 2;

        private final ToPrimitive.ToBoolean toBoolean;

        ToBoolean(ToPrimitive.ToBoolean toBoolean) {
            this.toBoolean = toBoolean;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doBoolean(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxBoolean((boolean) toBoolean.execute(value));
        }
    }

    @NodeInfo(shortName = "byte target type")
    abstract static class ToByte extends ToEspressoNode {
        protected static final int LIMIT = 2;

        private final ToPrimitive.ToByte toByte;

        ToByte(ToPrimitive.ToByte toByte) {
            this.toByte = toByte;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doByte(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxByte((byte) toByte.execute(value));
        }
    }

    @NodeInfo(shortName = "short target type")
    abstract static class ToShort extends ToEspressoNode {
        protected static final int LIMIT = 2;

        private final ToPrimitive.ToShort toShort;

        ToShort(ToPrimitive.ToShort toShort) {
            this.toShort = toShort;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doShort(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxShort((short) toShort.execute(value));
        }
    }

    @NodeInfo(shortName = "char target type")
    abstract static class ToChar extends ToEspressoNode {
        protected static final int LIMIT = 2;

        private final ToPrimitive.ToChar toChar;

        ToChar(ToPrimitive.ToChar toChar) {
            this.toChar = toChar;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doChar(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxCharacter((char) toChar.execute(value));
        }
    }

    @NodeInfo(shortName = "long target type")
    abstract static class ToLong extends ToEspressoNode {
        protected static final int LIMIT = 2;

        private final ToPrimitive.ToLong toLong;

        ToLong(ToPrimitive.ToLong toLong) {
            this.toLong = toLong;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doLong(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxLong((long) toLong.execute(value));
        }
    }

    @NodeInfo(shortName = "float target type")
    abstract static class ToFloat extends ToEspressoNode {
        protected static final int LIMIT = 2;

        private final ToPrimitive.ToFloat toFloat;

        ToFloat(ToPrimitive.ToFloat toLong) {
            this.toFloat = toLong;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doFloat(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxFloat((float) toFloat.execute(value));
        }
    }

    @NodeInfo(shortName = "double target type")
    abstract static class ToDouble extends ToEspressoNode {
        protected static final int LIMIT = 2;

        private final ToPrimitive.ToDouble toDouble;

        ToDouble(ToPrimitive.ToDouble toLong) {
            this.toDouble = toLong;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doDouble(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxDouble((double) toDouble.execute(value));
        }
    }

    @NodeInfo(shortName = "j.l.Object target type")
    @GenerateUncached
    public abstract static class ToJavaLangObject extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization
        public StaticObject doEspresso(StaticObject value) {
            return value;
        }

        @Specialization
        StaticObject doEspressoException(EspressoException value) {
            return value.getGuestException();
        }

        @Specialization
        StaticObject doHostString(String value, @Bind("getMeta()") Meta meta) {
            return meta.toGuestString(value);
        }

        @Specialization
        StaticObject doHostInteger(Integer value) {
            return getMeta().boxInteger(value);
        }

        @Specialization
        StaticObject doHostBoolean(Boolean value) {
            return getMeta().boxBoolean(value);
        }

        @Specialization
        StaticObject doHostByte(Byte value) {
            return getMeta().boxByte(value);
        }

        @Specialization
        StaticObject doHostChar(Character value) {
            return getMeta().boxCharacter(value);
        }

        @Specialization
        StaticObject doHostShort(Short value) {
            return getMeta().boxShort(value);
        }

        @Specialization
        StaticObject doHostLong(Long value) {
            return getMeta().boxLong(value);
        }

        @Specialization
        StaticObject doHostFloat(Float value) {
            return getMeta().boxFloat(value);
        }

        @Specialization
        StaticObject doHostDouble(Double value) {
            return getMeta().boxDouble(value);
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = {
                        "interop.isBoolean(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignBoolean(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return getMeta().boxBoolean(interop.asBoolean(value));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isBoolean returns true, asBoolean must succeed.");
            }
        }

        @Specialization(guards = {
                        "interop.isNumber(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNumber(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context) throws UnsupportedTypeException {
            if (interop.fitsInDouble(value)) {
                return StaticObject.createForeign(getLanguage(), getMeta().java_lang_Number, value, interop);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedTypeException.create(new Object[]{value}, "unsupported number");
        }

        @Specialization(guards = {
                        "interop.isString(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignString(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                String hostString = interop.asString(value);
                return meta.toGuestString(hostString);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
            }
        }

        @Specialization(guards = {
                        "interop.isException(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
                        "!isBoxedPrimitive(value)"
        })
        StaticObject doForeignException(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context) {
            return StaticObject.createForeignException(context, value, interop);
        }

        @Specialization(guards = {
                        "interop.hasArrayElements(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignArray(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeign(EspressoLanguage.get(this), getMeta().java_lang_Object, value, interop);
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.hasBufferElements(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignBuffer(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) {
            return StaticObject.createForeign(EspressoLanguage.get(this), meta._byte_array, value, interop);
        }

        @Specialization(guards = {
                        "!isTypeMappingEnabled(context)",
                        "interop.hasMetaObject(value)",
                        "!interop.hasArrayElements(value)",
                        "!interop.hasBufferElements(value)",
                        "!interop.isException(value)",
                        "!interop.isNumber(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
                        "!isBoxedPrimitive(value)"
        })
        StaticObject doJavaLangObjectForeignWrapper(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @Bind("getMeta()") Meta meta) {
            return StaticObject.createForeign(getLanguage(), meta.java_lang_Object, value, interop);
        }

        @Specialization(guards = {
                        "isTypeMappingEnabled(context)",
                        "interop.hasMetaObject(value)",
                        "!interop.hasArrayElements(value)",
                        "!interop.hasBufferElements(value)",
                        "!interop.isNumber(value)",
                        "!interop.isException(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
                        "!isBoxedPrimitive(value)",
        })
        StaticObject doForeignTypeMapping(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached BranchProfile errorProfile,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    return (StaticObject) converter.convert(StaticObject.createForeign(getLanguage(), meta.java_lang_Object, value, interop));
                } else {
                    // check if foreign exception
                    if (interop.isException(value)) {
                        return StaticObject.createForeignException(getContext(), value, interop);
                    }
                    // see if a generated proxy can be used for interface mapped types
                    ObjectKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, meta.java_lang_Object);
                    if (proxyKlass != null) {
                        return StaticObject.createForeign(getLanguage(), proxyKlass, value, interop);
                    }
                    return StaticObject.createForeign(getLanguage(), meta.java_lang_Object, value, interop);
                }
            } catch (ClassCastException e) {
                errorProfile.enter();
                throw UnsupportedTypeException.create(new java.lang.Object[]{value},
                                EspressoError.format("Could not cast foreign object to %s: due to: %s", meta.java_lang_Object.getNameAsString(), e.getMessage()));
            }
        }

        @Specialization(guards = {
                        "!interop.hasMetaObject(value)",
                        "!interop.hasArrayElements(value)",
                        "!interop.isBoolean(value)",
                        "!interop.isNumber(value)",
                        "!interop.isString(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignObject(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeign(EspressoLanguage.get(this), getMeta().java_lang_Object, value, interop);
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "java.lang.Object");
        }
    }

    @NodeInfo(shortName = "j.l.String target type")
    @GenerateUncached
    public abstract static class ToString extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        StaticObject doHostString(String value, @Bind("getMeta()") Meta meta) {
            return meta.toGuestString(value);
        }

        @Specialization(guards = "isEspressoString(value, meta)")
        StaticObject doEspressoString(StaticObject value, @Bind("getMeta()") @SuppressWarnings("unused") Meta meta) {
            return value;
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostString(value)",
                        "interop.isString(value)"
        })
        StaticObject doForeignString(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                String hostString = interop.asString(value);
                return meta.toGuestString(hostString);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
            }
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!interop.isString(value)"
        })
        StaticObject doUnsupported(Object value,
                        @Shared("value") @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_String.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "j.l.CharSequence target type")
    @GenerateUncached
    public abstract static class ToCharSequence extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        StaticObject doHostString(String value, @Bind("getMeta()") Meta meta) {
            return meta.toGuestString(value);
        }

        @Specialization(guards = "isEspressoString(value, meta)")
        StaticObject doEspressoString(StaticObject value, @Bind("getMeta()") @SuppressWarnings("unused") Meta meta) {
            return value;
        }

        @Specialization(guards = "!isEspressoString(value, meta)")
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_lang_CharSequence)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_CharSequence.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostString(value)",
                        "interop.isString(value)"
        })
        StaticObject doForeignString(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                String hostString = interop.asString(value);
                return meta.toGuestString(hostString);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_String.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "interface type mapping")
    public abstract static class ToMappedInterface extends ToEspressoNode {
        protected static final int LIMIT = 4;

        private final ObjectKlass targetType;

        ToMappedInterface(ObjectKlass targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "isHostObject(getContext(), value)"
        })
        StaticObject doForeignInterface(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InitCheck initCheck,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached BranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                ObjectKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), targetType);
                if (proxyKlass != null) {
                    initCheck.execute(targetType);
                    return StaticObject.createForeign(getLanguage(), proxyKlass, value, interop);
                }
                throw new ClassCastException();
            } catch (ClassCastException e) {
                errorProfile.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
            }
        }
    }

    @NodeInfo(shortName = "custom type converter")
    public abstract static class ToMappedType extends ToEspressoNode {
        protected static final int LIMIT = 4;

        private final ObjectKlass targetType;

        ToMappedType(ObjectKlass targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "isHostObject(getContext(), value)"
        })
        StaticObject doForeignConverter(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached BranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    return (StaticObject) converter.convert(StaticObject.createForeign(getLanguage(), targetType, value, interop));
                }
                throw new ClassCastException();
            } catch (ClassCastException e) {
                errorProfile.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
            }
        }
    }

    @NodeInfo(shortName = "Number type mapping")
    @GenerateUncached
    public abstract static class ToNumber extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_lang_Number)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_Number.getTypeAsString());
        }

        @Specialization
        StaticObject doHostInteger(Integer value) {
            return getMeta().boxInteger(value);
        }

        @Specialization
        StaticObject doHostByte(Byte value) {
            return getMeta().boxByte(value);
        }

        @Specialization
        StaticObject doHostShort(Short value) {
            return getMeta().boxShort(value);
        }

        @Specialization
        StaticObject doHostLong(Long value) {
            return getMeta().boxLong(value);
        }

        @Specialization
        StaticObject doHostFloat(Float value) {
            return getMeta().boxFloat(value);
        }

        @Specialization
        StaticObject doHostDouble(Double value) {
            return getMeta().boxDouble(value);
        }

        @Specialization(guards = {
                        "interop.isNumber(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNumber(Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            try {
                if (interop.fitsInByte(value)) {
                    return meta.boxByte(interop.asByte(value));
                }
                if (interop.fitsInShort(value)) {
                    return meta.boxShort(interop.asShort(value));
                }
                if (interop.fitsInInt(value)) {
                    return meta.boxInteger(interop.asInt(value));
                }
                if (interop.fitsInLong(value)) {
                    return meta.boxLong(interop.asLong(value));
                }
                if (interop.fitsInFloat(value)) {
                    return meta.boxFloat(interop.asFloat(value));
                }
                if (interop.fitsInDouble(value)) {
                    return meta.boxDouble(interop.asDouble(value));
                }
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Number.getTypeAsString());
            }
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Number.getTypeAsString());
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Number.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "byte array type mapping")
    @GenerateUncached
    public abstract static class ToByteArray extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta._byte_array)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta._byte_array.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.hasBufferElements(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
        })
        StaticObject doForeignBuffer(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) {
            return StaticObject.createForeign(EspressoLanguage.get(this), meta._byte_array, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta()._byte_array.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "array type mapping")
    public abstract static class ToArray extends ToEspressoNode {
        protected static final int LIMIT = 4;

        private final ArrayKlass targetType;

        protected ToArray(ArrayKlass targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "interop.hasArrayElements(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)"
        })
        StaticObject doForeignArray(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeign(EspressoLanguage.get(this), targetType, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "foreign exception type mapping")
    @GenerateUncached
    public abstract static class ToForeignException extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.isException(value)",
                        "!isEspressoException(value)"
        })
        StaticObject doForeignException(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
            return StaticObject.createForeignException(context, value, interop);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), getMeta().polyglot.ForeignException)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().polyglot.ForeignException.getTypeAsString());
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().polyglot.ForeignException.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "throwable type mapping")
    @GenerateUncached
    public abstract static class ToThrowable extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || meta.java_lang_Throwable.isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_Throwable.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspressoException(EspressoException value) {
            return value.getGuestException();
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.isException(value)",
                        "!isEspressoException(value)"
        })
        StaticObject doforeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
            return StaticObject.createForeignException(context, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Throwable.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "exception type mapping")
    @GenerateUncached
    public abstract static class ToException extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || meta.java_lang_Exception.isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_Exception.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspressoException(EspressoException value, @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            StaticObject guestException = value.getGuestException();
            if (meta.java_lang_Exception.isAssignableFrom(guestException.getKlass())) {
                return guestException;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_Exception.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.isException(value)",
                        "!isEspressoException(value)"
        })
        StaticObject doforeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
            return StaticObject.createForeignException(context, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Exception.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "runtime exception type mapping")
    @GenerateUncached
    public abstract static class ToRuntimeException extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || meta.java_lang_RuntimeException.isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_RuntimeException.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspressoException(EspressoException value, @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            StaticObject guestException = value.getGuestException();
            if (meta.java_lang_RuntimeException.isAssignableFrom(guestException.getKlass())) {
                return guestException;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_RuntimeException.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.isException(value)",
                        "!isEspressoException(value)"
        })
        StaticObject doforeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
            return StaticObject.createForeignException(context, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_RuntimeException.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "LocalDate type mapping")
    @GenerateUncached
    public abstract static class ToLocalDate extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_LocalDate)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_LocalDate.getTypeAsString());
        }

        @Specialization(guards = "interop.isDate(value)")
        StaticObject doForeignLocalDate(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalDate localDate = interop.asDate(value);
                return (StaticObject) meta.java_time_LocalDate_of.invokeDirect(null, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isDate returns true, asDate must succeed.");
            }
        }

        @Specialization(guards = "!interop.isDate(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_LocalDate.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "LocalTime type mapping")
    @GenerateUncached
    public abstract static class ToLocalTime extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_LocalTime)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_LocalTime.getTypeAsString());
        }

        @Specialization(guards = "interop.isTime(value)")
        StaticObject doForeignLocalTime(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalTime localTime = interop.asTime(value);
                return (StaticObject) meta.java_time_LocalTime_of.invokeDirect(null, localTime.getHour(), localTime.getMinute(), localTime.getSecond(), localTime.getNano());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isTime returns true, asTime must succeed.");
            }
        }

        @Specialization(guards = "!interop.isTime(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_LocalTime.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "LocalDateTime type mapping")
    @GenerateUncached
    public abstract static class ToLocalDateTime extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_LocalDateTime)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_LocalDateTime.getTypeAsString());
        }

        @Specialization(guards = {
                        "interop.isTime(value)",
                        "interop.isDate(value)"
        })
        StaticObject doForeignLocalDateTime(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalDate localDate = interop.asDate(value);
                LocalTime localTime = interop.asTime(value);
                StaticObject guestLocalDate = (StaticObject) meta.java_time_LocalDate_of.invokeDirect(null, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
                StaticObject guestLocalTime = (StaticObject) meta.java_time_LocalTime_of.invokeDirect(null, localTime.getHour(), localTime.getMinute(), localTime.getSecond(), localTime.getNano());

                return (StaticObject) meta.java_time_LocalDateTime_of.invokeDirect(null, guestLocalDate, guestLocalTime);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isDate returns true, asDate must succeed.");
            }
        }

        @Specialization(guards = {
                        "!interop.isTime(value)",
                        "!interop.isDate(value)"
        })
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_LocalDateTime.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "ZonedDateTime type mapping")
    @GenerateUncached
    public abstract static class ToZonedDateTime extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_ZonedDateTime)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_ZonedDateTime.getTypeAsString());
        }

        @Specialization(guards = {
                        "interop.isInstant(value)",
                        "interop.isTimeZone(value)"
        })
        StaticObject doForeignDateTime(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                ZoneId zoneId = interop.asTimeZone(value);
                StaticObject guestInstant = (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirect(null, instant.getEpochSecond(), (long) instant.getNano());
                StaticObject guestZoneID = (StaticObject) meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString(zoneId.getId()));
                return (StaticObject) meta.java_time_ZonedDateTime_ofInstant.invokeDirect(null, guestInstant, guestZoneID);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isTime returns true, asTime must succeed.");
            }
        }

        @Specialization(guards = {
                        "!interop.isInstant(value)",
                        "!interop.isTimeZone(value)"
        })
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_ZonedDateTime.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Instant type mapping")
    @GenerateUncached
    public abstract static class ToInstant extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_Instant)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_Instant.getTypeAsString());
        }

        @Specialization(guards = "interop.isInstant(value)")
        StaticObject doForeignInstant(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                return (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirect(null, instant.getEpochSecond(), (long) instant.getNano());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isInstant returns true, asInstant must succeed.");
            }
        }

        @Specialization(guards = "!interop.isInstant(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_Instant.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Duration type mapping")
    @GenerateUncached
    public abstract static class ToDuration extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_Duration)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_Duration.getTypeAsString());
        }

        @Specialization(guards = "interop.isDuration(value)")
        StaticObject doForeignDuration(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Duration duration = interop.asDuration(value);
                StaticObject guestDuration = meta.getAllocator().createNew(meta.java_time_Duration);
                meta.java_time_Duration_seconds.setLong(guestDuration, duration.getSeconds());
                meta.java_time_Duration_nanos.setInt(guestDuration, duration.getNano());
                return guestDuration;
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isDuration returns true, asDuration must succeed.");
            }
        }

        @Specialization(guards = "!interop.isDuration(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_Duration.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "ZoneId type mapping")
    @GenerateUncached
    public abstract static class ToZoneId extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_ZoneId)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_ZoneId.getTypeAsString());
        }

        @Specialization(guards = "interop.isTimeZone(value)")
        StaticObject doForeignZoneId(Object value, @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                ZoneId zoneId = interop.asTimeZone(value);
                return (StaticObject) meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString(zoneId.getId()));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isZoneId returns true, asTimeZone must succeed.");
            }
        }

        @Specialization(guards = "!interop.isTimeZone(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_ZoneId.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Date type mapping")
    @GenerateUncached
    public abstract static class ToDate extends ToEspressoNode {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_util_Date)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_util_Date.getTypeAsString());
        }

        @Specialization(guards = "interop.isInstant(value)")
        StaticObject doForeignDate(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                StaticObject guestInstant = (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirect(null, instant.getEpochSecond(), (long) instant.getNano());
                return (StaticObject) meta.java_util_Date_from.invokeDirect(null, guestInstant);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isInstant returns true, asInstant must succeed.");
            }
        }

        @Specialization(guards = "!interop.isInstant(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Date.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "unknown type mapping")
    public abstract static class ToUnknown extends ToEspressoNode {
        protected static final int LIMIT = 4;

        private final ObjectKlass targetType;

        ToUnknown(ObjectKlass targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || targetType.isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignWrapper(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            try {
                if (targetType.isInterface()) {
                    throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
                }
                checkHasAllFieldsOrThrow(value, targetType, interop, meta);
                return StaticObject.createForeign(getLanguage(), targetType, value, interop);
            } catch (ClassCastException ex) {
                throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
            }
        }

        @Specialization
        public StaticObject doUnknown(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }
    }

    static boolean isStaticObject(Object obj) {
        return obj instanceof StaticObject;
    }

    static boolean isHostString(Object obj) {
        return obj instanceof String;
    }

    static boolean isEspressoString(StaticObject obj, Meta meta) {
        return obj.getKlass() == meta.java_lang_String;
    }

    static boolean isBoxedPrimitive(Object obj) {
        return obj instanceof Number || obj instanceof Character || obj instanceof Boolean;
    }

    static boolean isEspressoException(Object obj) {
        return obj instanceof EspressoException;
    }

    static boolean isHostBoxed(Object obj) {
        return obj instanceof Number || obj instanceof Character || obj instanceof Boolean;
    }

    static boolean isTypeMappingEnabled(Klass klass) {
        EspressoContext context = klass.getContext();
        return context.getPolyglotInterfaceMappings().hasMappings() && context.getPolyglotInterfaceMappings().mapInterfaceName(klass) != null;
    }

    static boolean isTypeConverterEnabled(Klass klass) {
        EspressoContext context = klass.getContext();
        return context.getPolyglotInterfaceMappings().hasMappings() && context.getPolyglotInterfaceMappings().mapTypeConversion(klass) != null;
    }

    @Idempotent
    static boolean isTypeMappingEnabled(EspressoContext context) {
        return context.getPolyglotInterfaceMappings().hasMappings();
    }

    static boolean isHostObject(EspressoContext context, Object value) {
        return context.getEnv().isHostObject(value);
    }

    public static String getMetaName(Object metaObject, InteropLibrary interop) {
        assert interop.isMetaObject(metaObject);
        try {
            return interop.asString(interop.getMetaQualifiedName(metaObject));
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }
    }

    public static Object getMetaObjectOrThrow(Object value, InteropLibrary interop) throws ClassCastException {
        try {
            return interop.getMetaObject(value);
        } catch (UnsupportedMessageException e) {
            throw new ClassCastException("Could not lookup meta object");
        }
    }

    @TruffleBoundary
    public static void checkHasAllFieldsOrThrow(Object value, ObjectKlass klass, InteropLibrary interopLibrary, Meta meta) {
        CompilerAsserts.partialEvaluationConstant(klass);
        /*
         * For boxed types a .value member is not required if there's a direct conversion via
         * interop as* methods.
         */
        if (meta.isBoxed(klass)) {
            try {
                if ((klass == meta.java_lang_Integer && interopLibrary.fitsInInt(value)) ||
                                (klass == meta.java_lang_Long && interopLibrary.fitsInLong(value)) ||
                                (klass == meta.java_lang_Float && interopLibrary.fitsInFloat(value)) ||
                                (klass == meta.java_lang_Double && interopLibrary.fitsInDouble(value)) ||
                                (klass == meta.java_lang_Boolean && interopLibrary.isBoolean(value)) ||
                                (klass == meta.java_lang_Short && interopLibrary.fitsInShort(value)) ||
                                (klass == meta.java_lang_Byte && interopLibrary.fitsInByte(value)) ||
                                (klass == meta.java_lang_Character && interopLibrary.isString(value) && interopLibrary.asString(value).length() == 1)) {
                    return;
                }
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        for (Field f : klass.getDeclaredFields()) {
            if (!f.isStatic() && !interopLibrary.isMemberExisting(value, f.getNameAsString())) {
                throw new ClassCastException("Missing field: " + f.getNameAsString());
            }
        }
        if (klass.getSuperClass() != null) {
            checkHasAllFieldsOrThrow(value, klass.getSuperKlass(), interopLibrary, meta);
        }
    }
}
