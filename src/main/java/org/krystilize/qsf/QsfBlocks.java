package org.krystilize.qsf;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * This interface represents some number of ordered blocks.
 */
public interface QsfBlocks {

    static QsfBlocks collection(Collection<Block> blocks) {
        return blocks::forEach;
    }

    static QsfBlocks consumerRunner(Consumer<Consumer<Block>> consumerRunner) {
        return consumerRunner::accept;
    }

    /**
     * Iterates over the blocks in this section in order of x, y, z.
     *
     * @param consumer The consumer to call for each block.
     */
    void forEach(BlockConsumer consumer);

    /**
     * Gets the information associated with this section.
     * @return The information
     */
    default Information information() {
        long[] count = new long[1];
        forEach(block -> count[0]++);

        // 1 -> 1
        // 9 -> 3
        // 25 -> 5
        // 49 -> 7
        int width = (int) Math.ceil(Math.sqrt(count[0]));
        return new Information(width);
    }

    default boolean contentEquals(QsfBlocks decoded) {
        Queue<Block> blocks = new ArrayDeque<>();
        decoded.forEach(blocks::add);

        AtomicBoolean failed = new AtomicBoolean(false);

        forEach(block -> {
            Block poll = blocks.poll();
            if (poll == null) {
                failed.set(true);
                return;
            }

            if (!block.equals(poll)) {
                failed.set(true);
            }
        });

        return !failed.get();
    }

    record Information(int width) {
    }

    interface BlockConsumer extends Consumer<Block> {
    }

    interface Block {
        static Block of(String name) {
            return of(name, Map.of());
        }

        static Block of(String name, Map<String, String> properties) {
            return new QsfBlockRecord(name, properties);
        }

        String name();

        Map<String, String> properties();

        default Block copy() {
            String name = name();
            Map<String, String> properties = Map.copyOf(properties());
            return of(name, properties);
        }
    }
}
