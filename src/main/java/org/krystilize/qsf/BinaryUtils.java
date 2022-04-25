package org.krystilize.qsf;

import com.github.jinahya.bit.io.BitInput;
import com.github.jinahya.bit.io.BitOutput;

import java.io.IOException;

class BinaryUtils {

    public static int bitsToRepresent(long max) {
        int bits = 0;
        while (max > 0) {
            max >>= 1;
            bits++;
        }
        return bits;
    }

    public static void printLZSS(BitInput in) throws IOException {
        int searchBuffer = readUnit(12, in); // 1
        int lookAheadBuffer = readUnit(12, in); // 2
        int unitBitSize = readUnit(12, in); // 3
        int unitCount = readUnit(12, in); // 4

        int offsetSize = bitsToRepresent(searchBuffer);
        int lengthSize = bitsToRepresent(lookAheadBuffer);

        int unitsProcessed = 0;
        while (unitsProcessed < unitCount) {
            if (!in.readBoolean()) {
                System.out.println("Direct: " + readUnit(unitBitSize, in));
                unitsProcessed++;
            } else {
                int offset = readUnit(offsetSize, in);
                int length = readUnit(lengthSize, in);
                System.out.println("Match: " + offset + " | " + length);
                unitsProcessed += length;
            }
        }
    }

    /**
     * Reads a unit from the input list. This only supports up to 12-bit units.
     *
     * @param unitBitSize The size of the unit in bits.
     * @param in          The input list.
     * @return The unit.
     */
    public static short readUnit(int unitBitSize, BitInput in) throws IOException {
        assert unitBitSize > 0;
        assert unitBitSize <= 16;
        return (short) in.readUnsignedInt(unitBitSize);
    }

    /**
     * Write a unit to the output list. This only supports up to 12-bit units.
     *
     * @param unitBitSize The size of the unit in bits.
     * @param out         The output list.
     * @param unit        The unit to write.
     */
    public static void writeUnit(int unitBitSize, BitOutput out, short unit) throws IOException {
        assert unitBitSize > 0;
        assert unitBitSize <= 16;
        out.writeUnsignedInt(unitBitSize, unit);
    }

    public static void writeSizedString(BitOutput out, String str) throws IOException {
        out.writeInt32(str.length());
        for (int i = 0; i < str.length(); i++) {
            out.writeChar16(str.charAt(i));
        }
    }

    public static String readSizedString(BitInput in) throws IOException {
        int length = in.readInt32();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(in.readChar16());
        }
        return builder.toString();
    }

    public static void writeByteArray(BitOutput out, byte[] compressed) throws IOException {
        out.writeInt32(compressed.length);
        for (byte b : compressed) {
            out.writeByte8(b);
        }
    }

    public static byte[] readByteArray(BitInput in) throws IOException {
        int length = in.readInt32();
        byte[] compressed = new byte[length];
        for (int i = 0; i < length; i++) {
            compressed[i] = in.readByte8();
        }
        return compressed;
    }
}
