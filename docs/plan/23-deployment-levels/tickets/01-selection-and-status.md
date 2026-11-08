# Selection and status

Implement operator-authorized level selection and a status query that reports
requested level, active capabilities, and readiness separately. Persist the
selection and observed transition facts with replay-safe identities. Refuse an
unsupported value without changing current state.

Done when the same selection can be replayed without duplicate effects and a
failed capability start cannot appear ready.
