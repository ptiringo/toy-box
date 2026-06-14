# 0005. エンティティ識別子をタイムベース UUID（UUIDv7 相当）に統一する

- Status: Accepted
- Date: 2026-06-14
- Deciders: ptiringo

## Context（背景・課題）

エンティティは UUID ベースの同一性を持つ。その UUID の生成方法を決める必要がある。

- **`UUID.randomUUID()`（v4 ランダム）**: 標準で手軽だが、生成値に時間的順序がないため、永続化時に
  インデックス（特に B-Tree 系の主キーインデックス）の挿入位置が分散し、ページ分割・断片化を招きやすい。
- **タイムベース（UUIDv7 相当）**: 先頭にタイムスタンプを含むため、生成順にほぼ単調増加し、ソート可能で
  インデックス局所性に優れる。

また、生成方法がエンティティごとにバラつくと一貫性が保てない。

## Decision（決定）

識別子はタイムベース（UUIDv7 相当の `Generators.timeBasedEpochRandomGenerator()`、`java-uuid-generator`
ライブラリ）に**統一**する。

生成ロジックは `domain.shared.generateId()` に**集約**し、各 ID 値クラスは `JockeyId(generateId())` の
ように必ずこの関数を介して生成する（エンティティごとに生成方法を書き分けない）。

## Consequences（結果・影響）

- 生成値が時刻順にソート可能になり、永続化時のインデックス局所性が改善する。
- 生成方法が `generateId()` の一点に集約され、エンティティ間でブレない。
- `java-uuid-generator` への依存が増える（version catalog で管理）。
- ID 値クラスは `@JvmInline value class` + `@ValueObject` で型安全に表現する方針と組み合わせて運用する
  （詳細は CLAUDE.md「Entity パターン」「Value Object パターン」）。
