package org.whispersystems.signalservice.api.util;

import java.math.BigInteger;

public final class Uint64Util {

    private final static BigInteger MAX_UINT64 = uint64ToBigInteger(0xffffffffffffffffL);

    private Uint64Util() {
    }

    /**
     * Creates a {@link BigInteger} of the supplied value, treating it as
     * unsigned.
     */
    public static BigInteger uint64ToBigInteger(long uint64) {
        if (uint64 < 0) {
            int upper = (int) (uint64 >>> 32);
            int lower = (int) uint64;

            return (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).
                    add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
        } else {
            return BigInteger.valueOf(uint64);
        }
    }

    /**
     * Creates a long to be treated as unsigned of the supplied
     * {@link BigInteger}.
     *
     * @param value Must be in the range [0..2^64)
     * @throws Uint64RangeException iff value is outside of range.
     */
    public static long bigIntegerToUInt64(BigInteger value) throws Uint64RangeException {
        if (value.signum() < 0) {
            throw new Uint64RangeException("BigInteger out of uint64 range (negative)");
        }

        if (value.compareTo(MAX_UINT64) > 0) {
            throw new Uint64RangeException("BigInteger out of uint64 range (> MAX)");
        }

        return value.longValue();
    }
}
