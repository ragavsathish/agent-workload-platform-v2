# Private owner review workbench

This local workbench opens private candidate and quarantine inventories for owner review. It saves only explicit decisions. The review ledger is Grain; source text, expected labels, reviewer notes, and disposition reasons stay in owner-only files. Quality, migration, and live-read gates remain separate.

Use an existing private candidate corpus (`formatVersion: 2`) and a quarantine inventory from the same frozen snapshot. Keep all input and output files outside this repository with mode `0600`; use owner-only directories (`0700`). The owner identity must match the private owner map used for the frozen migration package.

```sh
python3 impl/private-review/workbench.py init \
  --workspace "$PRIVATE_DIR/review" --owner "$OWNER" \
  --candidates "$PRIVATE_DIR/pending-candidates.json" \
  --quarantine "$PRIVATE_DIR/normalized/quarantine.json" \
  --snapshot "$PRIVATE_DIR/oxigraph-snapshot.json"
python3 impl/private-review/workbench.py serve --workspace "$PRIVATE_DIR/review"
```

Open the printed loopback URL on the same machine. Paste the token from `review/owner-token.txt` into the page. Source text and model suggestions appear only after authentication. Choose a family and split; enter expected items as JSON with an exact source evidence quote and an origin (`observed`, `miss`, or `correction`). A reviewer must use a separate identity and token to approve, reject, or dispute the annotation. A different reviewer may adjudicate a dispute. The owner makes a separate privacy decision. No draft or model suggestion counts as approval.

Issue a reviewer token into an owner-only directory:

```sh
python3 impl/private-review/workbench.py add-reviewer \
  --workspace "$PRIVATE_DIR/review" --reviewer "$REVIEWER" \
  --token-file "$PRIVATE_DIR/review/reviewer-token.txt"
```

For an unlinked metadata record, retain quarantine or enter an evidence-backed exclusion. A link requires a source reference in the frozen snapshot and a reason and evidence. The page never infers a disposition. Export private handoff files to a new directory when ready:

```sh
python3 impl/private-review/workbench.py report \
  --workspace "$PRIVATE_DIR/review" --output-dir "$PRIVATE_DIR/review-report"
```

The report contains a quality corpus, readiness result, metadata disposition history, continuity approvals, and a private summary. The command prints only aggregate status. Review does not run quality evaluation, consume holdout, approve a migration cutover, or switch live reads. Reopen the same workspace to resume; action IDs, expected revisions, and Grain replay protect retry and concurrent edits.

See the [slice 21 plan](../../docs/plan/21-private-review-workbench/plan.md) and the [knowledge continuity procedure](../knowledge-migration/README.md).
