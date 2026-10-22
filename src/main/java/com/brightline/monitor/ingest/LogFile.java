package com.brightline.monitor.ingest;

import java.util.List;

public record LogFile(List<LogEntry> logs) {}
