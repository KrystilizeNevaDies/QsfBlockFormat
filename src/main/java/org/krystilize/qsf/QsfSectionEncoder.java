package org.krystilize.qsf;

import com.github.jinahya.bit.io.*;
import speiger.src.collections.objects.maps.impl.hash.Object2ShortOpenHashMap;
import speiger.src.collections.objects.maps.interfaces.Object2ShortMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A single threaded encoder for the QSF format.
 */
public record QsfSectionEncoder(Settings settings) {

    /**
     * Encodes the blocks.
     * @param blocks The blocks to encode.
     * @param out    The output to write to.
     */
    public void encode(QsfBlocks blocks, BitOutput out) {
        try {
            encodeUNSAFE(blocks, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Encodes the blocks.
     * @param blocks The blocks to encode.
     * @param out    The output to write to.
     */
    public void encode(QsfBlocks blocks, ByteOutput out) {
        encode(blocks, BitOutputAdapter.from(out));
    }

    /**
     * Encodes the blocks.
     * @param blocks The blocks to encode.
     * @param outStream The output to write to.
     */
    public void encode(QsfBlocks blocks, OutputStream outStream) {
        encode(blocks, StreamByteOutput.from(outStream));
    }

    /**
     * Encodes the blocks.
     * @param blocks The blocks to encode.
     * @return The encoded section.
     */
    public byte[] encode(QsfBlocks blocks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encode(blocks, out);
        return out.toByteArray();
    }

    private void encodeUNSAFE(QsfBlocks blocks, BitOutput out) throws IOException {

        // Load the palette first
        List<QsfBlocks.Block> palette = new ArrayList<>();
        Set<QsfBlocks.Block> allBlocks = new HashSet<>();
        Object2ShortMap<QsfBlocks.Block> paletteMap = new Object2ShortOpenHashMap<>();
        int[] blockSize = new int[]{0};

        blocks.forEach(block -> {
            blockSize[0]++;
            if (allBlocks.contains(block)) {
                return;
            }
            block = block.copy();
            palette.add(block);
            allBlocks.add(block);
            paletteMap.put(block, (short) (palette.size() - 1));
        });
        int paletteSize = palette.size();

        // Write the palette
        out.writeInt32(paletteSize);
        palette.forEach(block -> {
            // Block name
            try {
                BinaryUtils.writeSizedString(out, block.name());

                // Block properties
                out.writeInt32(block.properties().size());
                block.properties().forEach((key, value) -> {
                    try {
                        BinaryUtils.writeSizedString(out, key);
                        BinaryUtils.writeSizedString(out, value);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Each block is a palette index encoded in an integer number of bytes
        // Lets find out how many bits we need to encode a block
        int bitSize = Math.max((int) Math.ceil(Math.log(paletteSize) / Math.log(2)), 1);
        out.writeInt32(blockSize[0]);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BitOutput blocksOut = BitOutputAdapter.from(StreamByteOutput.from(outputStream));

        // Now we can write the blocks to the output
        blocks.forEach(block -> {
            short paletteIndex = paletteMap.getShort(block);

            // Write the palette index in the correct number of bits
            try {
                BinaryUtils.writeUnit(bitSize, blocksOut, paletteIndex);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Now we apply the LZSS compression
        byte[] compressed;
        try {
            compressed = LZSSUtil.compressLZSS((short) bitSize, (short) blockSize[0], outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Write the compressed data
        BinaryUtils.writeByteArray(out, compressed);
    }

    public record Settings() {
    }
}
