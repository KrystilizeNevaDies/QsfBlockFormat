package org.krystilize.qsf;

import com.github.jinahya.bit.io.BitInput;
import com.github.jinahya.bit.io.BitInputAdapter;
import com.github.jinahya.bit.io.ByteInput;
import com.github.jinahya.bit.io.StreamByteInput;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A single threaded decoder for the QSF format.
 */
public record QsfSectionDecoder(Settings settings) {

    /**
     * Decodes the blocks.
     * @param in the input
     * @return the decoded section
     */
    public QsfBlocks decode(BitInput in) {
        try {
            return decodeUNSAFE(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Decodes the blocks.
     * @param in the input
     * @return the decoded section
     */
    public QsfBlocks decode(ByteInput in) {
        return decode(BitInputAdapter.from(in));
    }

    /**
     * Decodes the blocks.
     * @param in the input stream
     * @return the decoded section
     */
    public QsfBlocks decode(InputStream in) {
        return decode(StreamByteInput.from(in));
    }

    /**
     * Decodes the blocks.
     * @param bytes the bytes
     * @return the decoded section
     */
    public QsfBlocks decode(byte[] bytes) {
        return decode(new ByteArrayInputStream(bytes));
    }

    private QsfBlocks decodeUNSAFE(BitInput in) throws IOException {

        // Decode palette
        int paletteSize = in.readInt32();
        List<QsfBlocks.Block> blocks = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            // Namespace
            String name = BinaryUtils.readSizedString(in);

            // Properties
            int propertiesSize = in.readInt32();
            //noinspection unchecked
            Map.Entry<String, String>[] properties = new Map.Entry[propertiesSize];
            for (int j = 0; j < propertiesSize; j++) {
                String propertyKey = BinaryUtils.readSizedString(in);
                String propertyValue = BinaryUtils.readSizedString(in);
                properties[j] = Map.entry(propertyKey, propertyValue);
            }

            blocks.add(QsfBlocks.Block.of(name, Map.ofEntries(properties)));
        }

        // Decode section
        int bitSize = Math.max((int) Math.ceil(Math.log(paletteSize) / Math.log(2)), 1);
        int bitsCount = in.readInt32();
        byte[] bytes = BinaryUtils.readByteArray(in);

        // Now we have all the data, we can decompress it

        // Apply decompression
        byte[] decompressed;
        try {
            decompressed = LZSSUtil.decompressLZSS(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Decode bit by bit
        BitInput blocksIn = BitInputAdapter.from(StreamByteInput.from(new ByteArrayInputStream(decompressed)));
        List<QsfBlocks.Block> blockList = new ArrayList<>(16 * 16 * 16);
        for (int i = 0; i < bitsCount; i++) {
            try {
                short index = BinaryUtils.readUnit(bitSize, blocksIn);
                blockList.add(blocks.get(index));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return QsfBlocks.collection(List.copyOf(blockList));
    }

    public record Settings() {
    }
}
