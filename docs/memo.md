現在のソースは伝票単位でデータ作成や、データ登録ができ、データ登録時にエラーが起きたら、伝票番号単位でロールバックできるようになっている？
はい、3つとも実現できています。コードの該当箇所と一緒に説明します。

1. 伝票単位でのデータ作成 ✅
SalesDocumentHandler.java:384-464 — buildSalesDocuments()


ZcSalesDocument（フラットなレコード群）
  ↓
Map<String, SalesDocBuildResult>  ← 伝票番号をキーに整理
  "4500000001" → { header, [item1, item2], [detail1, detail2, detail3] }
  "4500000002" → { header, [item1],        [detail1, detail2] }
headerSeenMap / itemSeenMap で「初出かどうか」を判定しながら、伝票番号単位に組み立てます。

2. 伝票単位でのDB登録 ✅
SalesDocumentHandler.java:448-500 — saveDocuments()


for (Map.Entry<...> entry : buildResults.entrySet()) {
    // 伝票1件ずつループ
    transactionTemplate.execute(status -> {
        db.run(Insert.into(HEADER_ENTITY).entry(result.header));   // ① ヘッダ
        db.run(Insert.into(ITEM_ENTITY).entries(items));           // ② 明細（バルク）
        db.run(Insert.into(DETAIL_ENTITY).entries(result.details)); // ③ 詳細（バルク）
        return null;
    });
}
3. 伝票単位でのロールバック ✅
SalesDocumentHandler.java:126-130 — init() で設定


transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
PROPAGATION_REQUIRES_NEW により、transactionTemplate.execute() を呼ぶたびに外側トランザクションとは独立した新しいトランザクションが開始されます。


CAP @On ハンドラ（外側トランザクション）
  ├─ 伝票A: execute() → 独立TX開始 → 成功 → COMMIT ✅  ← DBに残る
  ├─ 伝票B: execute() → 独立TX開始 → 成功 → COMMIT ✅  ← DBに残る
  └─ 伝票C: execute() → 独立TX開始 → INSERT失敗 → ROLLBACK ❌
                         ↑ RuntimeException が throw された時点で自動ロールバック
→ 伝票Cだけ取り消される。伝票A・Bは外側TXがロールバックしても影響なし。
catch ブロックで errorMessages に追記し errorCount++ してループを継続するため、伝票Cが失敗しても伝票D以降の処理は続きます。

実際にテストで検証済み
SalesDocumentHandlerTransactionTest.java でこの挙動を H2 実DBで確認しています：

テストケース	検証内容
saveDocuments_allSuccess_allCommitted	全件成功 → 全件コミット
saveDocuments_secondHeaderInsertFails_firstDocumentCommitted	2件目失敗 → 1件目はコミット済みのまま残る
saveDocuments_detailInsertFails_entireDocumentRolledBack	詳細INSERTで失敗 → 同一伝票のヘッダ・明細も含めてロールバック
saveDocuments_allFail_nothingCommitted	全件失敗 → DBに何も残らない
例外が発生してもUI側にctx.setResult()の内容が渡せる？
はい、どのパスを通っても必ず setResult() が実行される設計になっています。

例外が発生したときの流れ
onCreateSalesDocuments() の構造を見ると、setResult() の呼び出しが3箇所あり、すべてのパスをカバーしています。


// onCreateSalesDocuments() の構造
try {
    fetchZcSalesDocuments(...)  // ← ここだけ例外が外に出る可能性あり
    buildSalesDocuments(...)    // ← 内部でcatch済み、外には出ない
    saveDocuments(...)          // ← 内部でcatch済み、外には出ない
    setResult(ctx, ...)         // ← ① 正常系（エラーあり部分成功も含む）

} catch (DocumentProcessingException e) {
    setResult(ctx, false, ...)  // ← ② S4取得失敗

} catch (Exception e) {
    setResult(ctx, false, ...)  // ← ③ 予期しない例外の保険
}
// ← ここに必ず到達する
なぜ buildSalesDocuments の例外はUIに届くか
選択されている buildSalesDocuments() では例外を内部で吸収しています：


fetchMasterData → DocumentProcessingException をthrow
    ↓
buildSalesDocuments の catch(DocumentProcessingException) で止める
    → failedDocuments.add() + continue（次レコードへ）
    → buildSalesDocuments() は正常にreturnする（例外は外に出ない）
    ↓
onCreateSalesDocuments() の try ブロックは継続
    → saveDocuments() → setResult() に到達 ← ①のパス
エラー内容は errorMessages に収集してあるので、buildResultMessage() 経由で最終的に setResult() の message パラメータとしてUIに渡ります。

まとめ
発生箇所	例外の行き先	setResult() 呼ばれるか
fetchZcSalesDocuments	catch(DocumentProcessingException) ②へ	✅
fetchMasterData（ヘッダ/明細）	buildSalesDocuments 内でcatch	✅（①のパスで実行）
transactionTemplate.execute	saveDocuments 内でcatch	✅（①のパスで実行）
予期しない例外	catch(Exception) ③へ	✅
