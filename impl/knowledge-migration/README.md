# Private continuity rehearsal

The [slice 18 plan](../../docs/plan/18-private-knowledge-continuity/plan.md) defines the owner approval boundary. Use the pinned exporter in [snapshot.py](snapshot.py) to create a private source snapshot, owner map, normalized package, and quarantine ledger outside this repository. The continuity driver rechecks the live source on loopback and refuses a changed digest. Its output paths must be new and outside the repository.

```sh
python3 impl/knowledge-migration/continuity.py \
  --snapshot "$PRIVATE_DIR/oxigraph-snapshot.json" \
  --owner-map "$PRIVATE_DIR/owner-map.json" \
  --package "$PRIVATE_DIR/normalized/owner-package.json" \
  --quarantine "$PRIVATE_DIR/normalized/quarantine.json" \
  --storage-dir "$PRIVATE_DIR/shadow-grain" \
  --private-report "$PRIVATE_DIR/continuity-report.json"
```

The driver creates an isolated owner-only Grain directory and private row-level reports. It prints a content-free aggregate verdict. It never selects live reads. Keep the source snapshot and detailed reports for audit and rollback rehearsal. A zero-discrepancy report with unresolved quarantine is still an open shadow gate; a clean shadow gate still requires explicit owner approval before a live read choice.
