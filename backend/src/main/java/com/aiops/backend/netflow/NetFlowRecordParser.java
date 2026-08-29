package com.aiops.backend.netflow;

import java.util.List;

public interface NetFlowRecordParser {
    List<ParsedNetFlowRecord> parse(List<String> lines);
}
