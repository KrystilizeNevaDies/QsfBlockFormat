package org.krystilize.qsf;

import java.util.Map;

record QsfBlockRecord(String name, Map<String, String> properties) implements QsfBlocks.Block {
}
