CAP Javaのトランザクション仕組みと推奨方針
CAPのトランザクションモデル

HTTPリクエスト
  └─ CAP ChangeSet（= Springトランザクション）が自動で開始
       └─ @On イベントハンドラ 実行
            └─ ここで例外が伝播 → ChangeSet全体がROLLBACK
CAP JavaはSpringのトランザクション管理と統合されており、CAPの「ChangeSet」がSpringの@Transactionalと同じ仕組みで動作します。

伝票単位トランザクションの推奨実装方法
方法A（最推奨）: 別Serviceクラス + @Transactional(REQUIRES_NEW)


理由:
Springの@Transactionalはプロキシ経由で動作する。
同一クラス内のself-callではプロキシを経由しないため効かない。
→ 別のSpring Beanとして切り出すことで正しく動作する。

@Service
public class SalesDocPersistenceService {

    @Autowired
    private PersistenceService db;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveDocument(SalesDocBuildResult result, String docNum) {
        db.run(Insert.into(HEADER_ENTITY).entry(result.header));
        // items, details...
    }
}
方法B（現在の実装）: TransactionTemplate with REQUIRES_NEW


同一クラス内で完結できる。
プロキシ問題を回避できる。
ただし宣言的でなく、コードが少し複雑になる。
方法C: CAP ChangeSetContext API


CAP独自のAPI（runtime.changeSetContext()）で
CAPレイヤーのChangeSetを制御する方法もあるが、
CAP SDKのバージョン依存が大きく、現時点では
SpringトランザクションAPIを使う方がシンプル。
→ 今回は同一クラス内で完結させる要件のため方法B（現在実装）が妥当。
将来的に複数ハンドラから呼ばれるなら方法Aへリファクタ。

次に、コードにトランザクション説明コメントを追加します。

Edit SalesDocumentHandler.java
Added 22 lines
Edit SalesDocumentHandler.java
Added 27 lines
クラスのJavadocも処理フローに合わせて更新します。

Edit SalesDocumentHandler.java
Added 4 lines
コードへのコメント追加が完了しました。ポイントをまとめます。

推奨方針の整理
なぜ @Transactional を直接使わないのか

❌ 同一クラス内での self-call
    this.saveOneDocument(result)
    ↑ Springプロキシを経由しないため @Transactional が無効

✅ 別Beanへの呼び出し  ← 方法A（将来リファクタ候補）
    persistenceService.saveDocument(result)
    ↑ プロキシ経由 → @Transactional が有効

✅ TransactionTemplate  ← 方法B（今回の実装）
    transactionTemplate.execute(status -> { ... })
    ↑ プロキシ不要でプログラム的に制御
PROPAGATION_REQUIRES_NEW の動作イメージ

[外側: CAP ChangeSet]─────────────────────────────────────────────
 |
 ├─ execute() 呼び出し
 |   ↓ 外側トランザクションを suspend
 |   [内側: 伝票A専用トランザクション]
 |     db.run(INSERT header)
 |     db.run(INSERT items)   ← ここで例外
 |     → ROLLBACK（伝票Aのデータは消える）
 |   ↑ 外側トランザクションを resume
 |
 ├─ catch(Exception) で errorCount++、ループ継続
 |
 ├─ execute() 呼び出し
 |   ↓ 外側トランザクションを suspend
 |   [内側: 伝票B専用トランザクション]
 |     db.run(INSERT header)
 |     db.run(INSERT items)
 |     db.run(INSERT details)
 |     → COMMIT（伝票Bのデータは残る）
 |   ↑ 外側トランザクションを resume
[外側終了]───────────────────────────────────────────────────────
注意点: 外側のCAP ChangeSet との関係
REQUIRES_NEW でコミットされたデータは、外側CAP ChangeSetがロールバックしても取り消されない。これは今回の「伝票単位で独立させる」要件に合致した動作です。逆に言うと、「全部成功した場合のみ全件コミット」という要件には向かないため、要件に応じて使い分けが必要です。