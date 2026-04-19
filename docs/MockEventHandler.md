# CAP Java モックイベントハンドラ 作成手順

外部サービス（S/4HANA ODataサービス等）への接続をインターセプトし、  
単体テストや結合テストで任意の挙動（例外・正常応答）を再現するためのモックハンドラ作成手順。

---

## ディレクトリ構成

```
srv/src/main/java/customer/{project}/
└── handler/
    └── mock/
        ├── ZcSalesDocumentMock.java      # Pattern A: 常に例外
        └── ZsSalesdocUpdateSrvMock.java  # Pattern B: N回目だけ例外
```

> **NOTE:** モッククラスは `src/main` に置く。`src/test` に置くと SpringBoot コンテキスト起動時に
> スキャン対象外になりBeanが登録されない。

---

## 基本構成（全パターン共通）

```java
@Component
@Profile("mocked")                          // (1) プロファイル指定
@ServiceName("ZC_SALESDOCUMENT_SERVICE")    // (2) インターセプト対象サービス名
public class XxxMock implements EventHandler {
    ...
}
```

### (1) `@Profile("mocked")` の意味

- このBeanは Spring プロファイル `mocked` が有効な場合のみロードされる。
- 通常起動・本番環境では一切影響しない。
- テスト実行時は `@ActiveProfiles("mocked")` で有効化する。

### (2) `@ServiceName` の指定

- CAP のサービスディスパッチャが参照するキー。
- `.cdsrc.json` や外部サービス定義に記載されているサービス名（技術名）をそのまま使用する。
- 誤ったサービス名を指定するとインターセプトが一切かからないため注意。

---

## Pattern A: 常に例外をスローする

外部サービス呼び出しが常に失敗するシナリオ（catch 節の動作確認）に使用する。

```java
@Component
@Profile("mocked")
@ServiceName("ZC_SALESDOCUMENT_SERVICE")
public class ZcSalesDocumentMock implements EventHandler {

    private static final ErrorStatus SERVICE_UNAVAILABLE = new ErrorStatus() {
        @Override public int getHttpStatus()    { return 503; }
        @Override public String getCodeString() { return "SERVICE_UNAVAILABLE"; }
    };

    @On(event = CqnService.EVENT_READ, entity = "ZC_SALESDOCUMENT_SERVICE.ZcSalesDocument")
    public void onRead() {
        throw new ServiceException(
                SERVICE_UNAVAILABLE,
                "ZC_SALESDOCUMENT_SERVICE への接続に失敗しました（モック）");
    }
}
```

### なぜ `@On` を使うのか（`@Before` ではなく）

| アノテーション | 用途 |
|---|---|
| `@Before` | バリデーション・前処理。例外をスローすると `@On` に進まず伝播する。 |
| `@On` | メイン処理の置き換え。CAP 組み込みの `@On`（S4 への実呼び出し）を上書きする。 |

**`@ServiceName` を持つ Bean を登録すると CAP の組み込み `@On` は無効化される。**  
`@On` を自前で実装しないと "No ON handler completed the processing" エラーになる。  
常に例外を返す場合も `@On` に書くことで組み込みハンドラを正しく上書きできる。

### `@Before` で例外をスローした場合の伝播経路

```
@Before → ServiceException throw
  → @On には到達しない
    → s4Service.run() の呼び出し元に例外伝播
```

`@Before` のみで実装した場合も例外は呼び出し元に伝播するが、  
組み込みの `@On` も残存するため挙動が不安定になりやすい。**Pattern A では `@On` に統一する。**

---

## Pattern B: N回目だけ例外をスローする（部分成功シナリオ）

「1件目は成功・2件目は失敗」など、部分成功の境界動作を確認したい場合に使用する。

```java
@Component
@Profile("mocked")
@ServiceName("ZS_SALESDOC_UPDATE_SRV")
public class ZsSalesdocUpdateSrvMock implements EventHandler {

    private static final ErrorStatus SERVICE_UNAVAILABLE = new ErrorStatus() {
        @Override public int getHttpStatus()    { return 503; }
        @Override public String getCodeString() { return "SERVICE_UNAVAILABLE"; }
    };

    private final AtomicInteger callCount = new AtomicInteger(0);

    /** 2回目以降で例外をスロー */
    @Before(event = CqnService.EVENT_CREATE, entity = "UpdateSalesDocStatus")
    public void beforeCreate() {
        int count = callCount.incrementAndGet();
        if (count == 1) return;   // 1回目: @On に進む
        throw new ServiceException(SERVICE_UNAVAILABLE, "接続エラー（モック）");
    }

    /** 1回目のみ到達。INSERT を正常完了させる */
    @On(event = CqnService.EVENT_CREATE, entity = "UpdateSalesDocStatus")
    public void onCreate(EventContext ctx) {
        // 呼び出し元が戻り値を使わない場合は空実装で可
    }

    /** テスト間でカウンタをリセットする */
    public void reset() {
        callCount.set(0);
    }
}
```

### Pattern B の `@Before` + `@On` 両方が必要な理由

1. `@Before` でカウンタをチェックし、条件次第で例外をスロー。
2. `@Before` が正常通過（return）すると `@On` に処理が移る。
3. `@ServiceName` で組み込み `@On` が無効化されるため、自前の `@On` が必須。

`@On` を省略すると 1回目の正常呼び出し時に  
"No ON handler completed the processing" が発生する。

### `reset()` メソッドについて

テスト間でカウンタが引き継がれると期待する呼び出し回数がずれる。  
必ずテストの `@BeforeEach` で呼び出すこと。

```java
@BeforeEach
void setUp(@Autowired ZsSalesdocUpdateSrvMock mock) {
    mock.reset();
}
```

---

## `ErrorStatus` のインライン実装について

CAP の `ErrorStatuses` ユーティリティには 503 が定義されていないため、  
`ErrorStatus` インターフェースを匿名クラスでインライン実装する。

```java
private static final ErrorStatus SERVICE_UNAVAILABLE = new ErrorStatus() {
    @Override public int getHttpStatus()    { return 503; }
    @Override public String getCodeString() { return "SERVICE_UNAVAILABLE"; }
};
```

他に使いたいステータスコードも同様にインライン実装する。

---

## テストクラスでの有効化

```java
@SpringBootTest
@ActiveProfiles("mocked")   // ← モックBeanをロードする
class MyServiceTest {

    @Autowired
    ZsSalesdocUpdateSrvMock mock;   // reset() を呼ぶために Autowired

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void test() {
        // モックが差し込まれた状態でサービスを呼び出す
    }
}
```

---

## `application.yaml` の設定

テスト実行時も `application.yaml` の設定が読み込まれる。  
外部サービス呼び出しや認証がデフォルトで有効にならないよう以下を設定する。

```yaml
spring:
  sql.init.platform: h2
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  security:
    user:
      name: admin
      password: admin

cds:
  data-source.auto-config.enabled: false   # 外部DBへの自動接続を無効化
  security:
    mock:
      enabled: true                         # CDS 認証をモックに切り替え
```

> **NOTE:** `cds.security.mock.enabled: true` を設定しないと、
> テスト実行時に認証エラーが発生してコンテキスト起動に失敗する場合がある。

---

## パターン選択の指針

| 確認したい内容 | 使うパターン |
|---|---|
| 外部サービス呼び出しの catch 節の動作 | **Pattern A**（常に例外） |
| 部分成功時の業務フロー（N件中M件失敗） | **Pattern B**（N回目に例外） |
| 正常系（外部サービス接続あり） | モックなし（`@Profile("mocked")` を付与しない） |

---

## よくあるミス

| 症状 | 原因 | 対処 |
|---|---|---|
| モックが動いていない | `@ActiveProfiles("mocked")` が抜けている | テストクラスに追加する |
| "No ON handler completed the processing" | `@On` の実装が欠落している | Pattern B では `@Before` と `@On` を両方実装する |
| テスト間で挙動がずれる | `reset()` を `@BeforeEach` で呼んでいない | `@BeforeEach` に `mock.reset()` を追加する |
| `@ServiceName` のサービス名が違う | 技術名のスペルミス | CDS 定義や `.cdsrc.json` で正確なサービス名を確認する |
| モックが本番にも影響する | `@Profile("mocked")` を付け忘れている | 必ず付与する |
