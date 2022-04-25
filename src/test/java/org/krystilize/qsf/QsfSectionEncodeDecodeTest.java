package org.krystilize.qsf;

import com.github.jinahya.bit.io.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class QsfSectionEncodeDecodeTest {

    @Test
    public void testEncodeDecode() {
        for (int count = 8; count < 64; count += 8) {
            testEncodeDecode(count);
        }
    }

    @Test
    public void testWriteRead() {
        Random random = new Random(0);
        for (int size = 1; size < 17; size++) {
            short value = (short) random.nextInt((int) Math.pow(2, size));

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BitOutput out = BitOutputAdapter.from(StreamByteOutput.from(outputStream));

            try {
                BinaryUtils.writeUnit(size, out, value);
                out.align();
            } catch (IOException e) {
                fail(e);
            }

            byte[] bytes = outputStream.toByteArray();
            BitInput in = BitInputAdapter.from(StreamByteInput.from(new ByteArrayInputStream(bytes)));

            try {
                short actual = BinaryUtils.readUnit(size, in);
                assertEquals(value, actual);
            } catch (IOException e) {
                fail(e);
            }
        }
    }

    private void testEncodeDecode(int count) {
        var encoder = new QsfSectionEncoder(new QsfSectionEncoder.Settings());
        var decoder = new QsfSectionDecoder(new QsfSectionDecoder.Settings());
        QsfBlocks blocks = blocks(count);

        // Write to a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BitOutput out = BitOutputAdapter.from(StreamByteOutput.from(outputStream));
        encoder.encode(blocks, out);

        byte[] encoded = outputStream.toByteArray();

        // Read back from the byte array
        BitInput in = BitInputAdapter.from(StreamByteInput.from(new ByteArrayInputStream(encoded)));
        QsfBlocks decoded = decoder.decode(in);

        List<QsfBlocks.Block> expected = new ArrayList<>();
        blocks.forEach(expected::add);

        List<QsfBlocks.Block> actual = new ArrayList<>();
        decoded.forEach(actual::add);

        assertEquals(expected.size(), actual.size(), "Expected and actual block count do not match");

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i));
        }

        assertTrue(blocks.contentEquals(decoded), "Decoded section blocks did not match with settings: count=" + count);
    }


    private QsfBlocks blocks(int count) {
        var random = new Random(0);
        var blocks = new QsfBlocks.Block[count];

        for (int i = 0; i < count; i++) {
            byte[] array = new byte[10];
            random.nextBytes(array);
            String name = new String(array, StandardCharsets.UTF_8);
            blocks[i] = QsfBlocks.Block.of(name);
        }

        return QsfBlocks.consumerRunner(consumer -> {
            int i = 0;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        i++;
                        consumer.accept(blocks[i % count]);
                    }
                }
            }
        });
    }
}