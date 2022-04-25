package org.krystilize.qsf;

import com.github.jinahya.bit.io.*;
import speiger.src.collections.shorts.lists.ShortArrayList;
import speiger.src.collections.shorts.lists.ShortList;
import speiger.src.collections.shorts.queues.ShortArrayFIFOQueue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;

class LZSSUtil {
    /**
     * Compresses the given data using LZSS. This assumes that the data units are byte-based.
     *
     * @param data The data to compress.
     * @return The compressed data as a byte[]
     * @throws IOException If the input is not valid.
     */
    public static byte[] compressLZSS(byte[] data) throws IOException {
        return compressLZSS((short) Byte.SIZE, (short) data.length, data);
    }

    /**
     * Compresses the given data using LZSS.
     *
     * @param unitBitSize The size of the unit in bits.
     * @param unitCount   The amount of units to compress.
     * @param data        The data to compress.
     * @return The compressed data as a byte[]
     * @throws IOException If the input is not valid.
     */
    public static byte[] compressLZSS(short unitBitSize, short unitCount, byte[] data) throws IOException {
        BitInput in = BitInputAdapter.from(StreamByteInput.from(new ByteArrayInputStream(data)));
        return compressLZSS(unitBitSize, unitCount, in);
    }

    /**
     * Compresses the given data using LZSS.
     *
     * @param unitBitSize The size of the unit in bits.
     * @param unitCount   The amount of units to compress.
     * @param in          The input list.
     * @return The compressed data as a byte[]
     * @throws IOException If the input is not valid.
     */
    public static byte[] compressLZSS(short unitBitSize, short unitCount, BitInput in) throws IOException {
        return compressLZSS((short) 127, (short) 32, unitBitSize, unitCount, in);
    }

    /**
     * Compresses the given data using LZSS.
     *
     * @param searchBuffer    The size of the search buffer.
     * @param lookAheadBuffer The size of the look ahead buffer.
     * @param unitBitSize     The size of the unit in bits.
     * @param unitCount       The amount of units to compress.
     * @param in              The input list.
     * @return The compressed data as a byte[]
     * @throws IOException If the input is not valid.
     */
    public static byte[] compressLZSS(short searchBuffer, short lookAheadBuffer, short unitBitSize, short unitCount,
                                      BitInput in) throws IOException {
        // Encoding:
        // 1. Search buffer size (int12)
        // 2. Lookahead buffer size (int12)
        // 3. Unit size (int12)
        // 4. Unit count (int12)
        // The rest is the actual data:
        // 0b0 -> unit
        // 0b1 -> offset | length

        // Output
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BitOutput out = BitOutputAdapter.from(StreamByteOutput.from(outputStream));

        // 1, 2, 3:
        BinaryUtils.writeUnit(12, out, searchBuffer);
        BinaryUtils.writeUnit(12, out, lookAheadBuffer);
        BinaryUtils.writeUnit(12, out, unitBitSize);
        BinaryUtils.writeUnit(12, out, (short) (unitCount - 1));

        // The rest is the actual data
        ShortArrayFIFOQueue buffer = new ShortArrayFIFOQueue();
        ShortArrayFIFOQueue lookahead = new ShortArrayFIFOQueue();

        int offsetSize = BinaryUtils.bitsToRepresent(searchBuffer);
        int lengthSize = BinaryUtils.bitsToRepresent(lookAheadBuffer);

        for (int i = 0; i < searchBuffer; i++) {
            buffer.enqueue((short) 0);
        }
        for (int i = 0; i < lookAheadBuffer; i++) {
            unitCount--;
            lookahead.enqueue(BinaryUtils.readUnit(unitBitSize, in));
        }

        WHILE_LOOP:
        while (!lookahead.isEmpty() && unitCount >= 0) {

            // Attempt to find a match in the buffer

            // For each possible size of the match, highest to lowest
            MATCH_SIZE_LOOP:
            for (short i = (short) lookahead.size(); i >= 1; i--) {

                // For each index the match could start at
                MATCH_LOOP:
                for (short j = (short) (searchBuffer - i); j > 0; j--) {

                    // Check for the match
                    for (short k = 0; k < i; k++) {
                        short lookaheadValue = lookahead.peek(k);
                        short bufferValue = buffer.peek(j + k);
                        if (lookaheadValue != bufferValue) {
                            continue MATCH_LOOP;
                        }
                    }

                    // Match found
                    final short offset = (short) (searchBuffer - j);
                    //noinspection UnnecessaryLocalVariable
                    final short length = i;

                    // Dont write the match if it will take up more space than writing directly
                    int sizeForMatch = 1 + offsetSize + lengthSize;
                    int sizeForDirect = (1 + unitBitSize) * length;
                    if (sizeForMatch > sizeForDirect) {
                        break MATCH_SIZE_LOOP;
                    }

                    // Write the match
                    out.writeBoolean(true); // 0b1

                    // Offset then length
                    BinaryUtils.writeUnit(offsetSize, out, offset);
                    BinaryUtils.writeUnit(lengthSize, out, length);

                    for (int k = 0; k < length; k++) {
                        buffer.dequeue();
                        buffer.enqueue(lookahead.dequeue());
                        if (unitCount > 0) {
                            unitCount--;
                            lookahead.enqueue(BinaryUtils.readUnit(unitBitSize, in));
                        }
                    }
                    continue WHILE_LOOP;
                }
            }

            // Cannot find a match, so add the short directly to the buffer
            out.writeBoolean(false); // 0b0
            buffer.dequeue();
            short value = lookahead.dequeue();
            buffer.enqueue(value);
            BinaryUtils.writeUnit(unitBitSize, out, value);
            if (unitCount > 0) {
                unitCount--;
                lookahead.enqueue(BinaryUtils.readUnit(unitBitSize, in));
            }
        }
        out.align();
        return outputStream.toByteArray();
    }

    /**
     * Decompresses the given data using LZSS.
     *
     * @param compressed The compressed data.
     * @return The decompressed data as a byte[]
     * @throws IOException If the input is not valid.
     */
    public static byte[] decompressLZSS(byte[] compressed) throws IOException {
        BitInput in = BitInputAdapter.from(StreamByteInput.from(new ByteArrayInputStream(compressed)));
        return decompressLZSS(in);
    }

    /**
     * Decompresses the given data using LZSS.
     *
     * @param in The input stream.
     * @return The decompressed data as a byte[]
     * @throws IOException If the input is not valid.
     */
    public static byte[] decompressLZSS(BitInput in) throws IOException {
        // Encoding:
        // 1. Search buffer size (int12)
        // 2. Lookahead buffer size (int12)
        // 3. Unit size (int12)
        // 4. Unit count (int12)
        // The rest is the actual data:
        // 0b0 -> unit
        // 0b1 -> offset | length

        final int searchBuffer = BinaryUtils.readUnit(12, in); // 1
        final int lookAheadBuffer = BinaryUtils.readUnit(12, in); // 2
        final int unitBitSize = BinaryUtils.readUnit(12, in); // 3
        final int unitCount = BinaryUtils.readUnit(12, in) + 1; // 4

        final int offsetSize = BinaryUtils.bitsToRepresent(searchBuffer);
        final int lengthSize = BinaryUtils.bitsToRepresent(lookAheadBuffer);

        // Output
        ShortList out = new ShortArrayList();
        for (int i = 0; i < searchBuffer; i++) {
            out.add((short) 0);
        }

        // The rest is the actual data

        int unitsProcessed = 0;
        while (unitsProcessed < unitCount) {
            try {
                if (!in.readBoolean()) { // 0b0
                    // Unit
                    short unit = BinaryUtils.readUnit(unitBitSize, in);
                    out.add(unit);
                    unitsProcessed++;
                } else {
                    // Match
                    // Offset then length
                    short offset = BinaryUtils.readUnit(offsetSize, in);
                    short length = BinaryUtils.readUnit(lengthSize, in);

                    int listSize = out.size();
                    for (short i = offset; i > 0; i--) {
                        int index = listSize - i;
                        out.add(out.getShort(index));
                        unitsProcessed++;
                    }
                }
            } catch (EOFException e) {
                throw new RuntimeException(e);
            }
        }

        for (int i = 0; i < searchBuffer; i++) {
            out.removeShort(0);
        }

        // Convert to bits
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BitOutput byteOut = BitOutputAdapter.from(StreamByteOutput.from(outputStream));
        for (short value : out) {
            BinaryUtils.writeUnit(unitBitSize, byteOut, value);
        }
        return outputStream.toByteArray();
    }
}
