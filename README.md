# integration-health-monitor
Dashboard that triages integrations into Green / Amber / Red from raw request logs. A scheduler evaluates every 60 s and writes one snapshot row per integration; pages only read snapshots.
