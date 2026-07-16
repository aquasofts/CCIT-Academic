# Tieba integration notice

The `feature:tieba` module is derived from ideas and selected implementation logic in
[TiebaLite](https://github.com/0ranko0P/TiebaLite) at commit
`910fd564c47f77ab6a807f1bc122279e7b9aa0b1`.

Copyright for the original TiebaLite work remains with its contributors. TiebaLite is
licensed under GNU GPL version 3. This integration is distributed under the same license.

Changes made for CCIT Academic include:

- limiting native routing and automatic sign-in to `长春工程学院`;
- removing all forum write operations except the single-forum sign request;
- replacing the upstream UI with the CCIT Material 3 theme and a nested Navigation Compose graph;
- using a single local Room account and domain-scoped WebView cookie cleanup;
- loading Tieba CDN display images first and opening the original-quality URL in the viewer;
- rendering protobuf rich content, bundled classic emoticons, and nested replies with the
  upstream TiebaLite assets and mapping rules;
- adapting TiebaLite's one-time WorkManager rescheduling model to one forum and one account.

The upstream repository remains available as the pinned `TiebaLite` git submodule. The
full GPLv3 text is in the repository root `LICENSE` and in `TiebaLite/LICENSE`.
