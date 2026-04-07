// cds import ZS_SALESDOC_UPDATE_SRV.edmx によって生成される想定のファイル。
// Source: S/4HANA カスタム更新サービス - 売上伝票ステータス更新 / ログ登録 (OData V2)
// 呼び出し方式: CqnInsert (POST) のみ。p_key / c_key はダミーキー（空文字を設定）

/* checksum : 4d7a1c9e2f5b8d03 */

@cds.external        : true
@cds.persistence.skip: true
service ZS_SALESDOC_UPDATE_SRV {

  // ----------------------------------------------------------------
  // UpdateSalesDocStatus
  // 売上伝票ステータス更新エンティティ
  //
  // S4 側では POST を受けて内部テーブルを更新する。
  // CAP 側は CqnInsert で呼び出す（CqnUpdate は @cds.updatable:false のため不可）。
  // p_key / c_key はダミーキー: 業務的意味なし・null は S4 エラーになるため "" を設定する。
  // 全項目 not null: 更新不要な項目は "" または "0" を設定する。
  // ----------------------------------------------------------------
  @cds.updatable   : false
  @cds.deletable   : false
  @cds.filterable  : false
  entity UpdateSalesDocStatus {
    key p_key             : String(1)   not null;   // ダミーキー（常に ""）
    key c_key             : String(1)   not null;   // ダミーキー（常に ""）
        SalesDocument     : String(10)  not null;   // 売上伝票番号
        SalesDocumentItem : String(6)   not null;   // 明細番号
        ProcessingStatus  : String(2)   not null;   // 処理ステータス
        ProcessedDate     : String(10)  not null;   // 処理日 (YYYY-MM-DD)
        ProcessedBy       : String(12)  not null;   // 処理者
        Remark            : String(255) not null;   // 備考
        InternalCode      : String(20)  not null;   // 内部コード
  };

  // ----------------------------------------------------------------
  // InsertSalesDocLog
  // 売上伝票ログ登録エンティティ
  //
  // S4 側では POST を受けてログテーブルへレコードを追加する。
  // CAP 側は CqnInsert で呼び出す。
  // p_key / c_key はダミーキー: "" を設定する。
  // ----------------------------------------------------------------
  @cds.updatable   : false
  @cds.deletable   : false
  @cds.filterable  : false
  entity InsertSalesDocLog {
    key p_key             : String(1)   not null;   // ダミーキー（常に ""）
    key c_key             : String(1)   not null;   // ダミーキー（常に ""）
        SalesDocument     : String(10)  not null;   // 売上伝票番号
        SalesDocumentItem : String(6)   not null;   // 明細番号
        LogType           : String(1)   not null;   // ログ種別 (S=成功 / E=エラー)
        LogMessage        : String(255) not null;   // ログメッセージ
  };

}
