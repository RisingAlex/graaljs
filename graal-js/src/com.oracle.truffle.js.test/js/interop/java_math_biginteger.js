/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/*
 * Test java.math.BigInteger interop.
 */

load('../assert.js');

const BigInteger = java.math.BigInteger;

let zero = BigInteger.ZERO;
let small= BigInteger.valueOf(42);
let long = new BigInteger("9223372036854775807"); // Long.MAX_VALUE
let big  = new BigInteger("100000000000000000000"); // 10n ** 20n
let huge = BigInteger.valueOf(2).pow(1000);
let bigIntegers = [zero, small, long, big, huge];

function nonStrictThis() {
    return this;
}

function int64(b) {
    return b.longValue();
}

function uint64(b) {
    return b.mod(BigInteger.ONE.shiftLeft(64));
}

for (let b of bigIntegers) {
    // typeof value is 'number' for java.math.BigInteger
    assertSame('number', typeof b);
    assertTrue(typeof b == 'number');
    assertSame('number', typeof Object(b));
    assertTrue(typeof Object(b) == 'number');
    assertSame('number', typeof nonStrictThis.call(b));
    assertTrue(typeof nonStrictThis.call(b) == 'number');

    assertTrue(b == b.multiply(BigInteger.ONE));
    // ToObject should keep foreign members
    assertTrue(b == Object(b).multiply(BigInteger.ONE));
    assertTrue(b == nonStrictThis.call(b).multiply(BigInteger.ONE));

    assertTrue(b == Object(b));
    assertTrue(Object(b) == b);
    assertTrue(b == BigInt(b));
    assertTrue(BigInt(b) == b);

    // ToObject does not wrap BigInteger.
    assertTrue(b === Object(b));

    // Number (double) coercion
    assertSame(Number(b), Number.prototype.valueOf.call(b));
    assertSame(String(+b), Number.prototype.toString.call(b));

    // Avoid double coercion in ToString.
    assertSame(b.toString(), String(b));

    // ToBigInt does not accept Number, require explicit conversion for now.
    let i64a = new BigInt64Array(1);
    let u64a = new BigUint64Array(1);
    let cast = BigInt(b);
    i64a[0] = cast;
    u64a[0] = cast;
    assertSame(i64a[0], BigInt.asIntN(64, cast));
    assertSame(u64a[0], BigInt.asUintN(64, cast));
    assertTrue(i64a[0] == int64(b));
    assertTrue(u64a[0] == uint64(b));
}
